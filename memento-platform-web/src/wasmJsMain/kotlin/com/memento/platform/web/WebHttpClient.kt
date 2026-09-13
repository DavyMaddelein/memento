package com.memento.platform.web

import com.memento.platform.contract.NominatimReverseGeocodingService
import com.memento.platform.contract.PlatformHttpClient
import com.memento.platform.contract.ReverseGeocodingService
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.JsAny
import kotlin.js.JsName
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [PlatformHttpClient] backed by the browser Fetch API.
 *
 * Headers are forwarded as-is; the browser silently drops forbidden ones such as `User-Agent`, so
 * only `Accept` reaches the network. A ~6s timeout is enforced by [withTimeoutOrNull]: when the
 * deadline fires the suspending call is cancelled, which invokes [WasmAbortController.abort] and
 * terminates the in-flight request. Network errors and non-2xx responses fail the [Result].
 */
class WebHttpClient(
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) : PlatformHttpClient {

    override suspend fun get(url: String, headers: Map<String, String>): Result<String> = try {
        val controller = newAbortController()
        val init = buildRequestInit(createHeaders(headers), controller.signal)
        val body = withTimeoutOrNull(timeoutMillis) {
            suspendCancellableCoroutine<String> { continuation ->
                browserFetch(url, init)
                    .then { response ->
                        if (response.ok) {
                            response.text()
                                .then { text ->
                                    if (continuation.isActive) continuation.resume(text)
                                }
                                .`catch` { error ->
                                    if (continuation.isActive) {
                                        continuation.resumeWithException(
                                            IllegalStateException("Failed to read body for $url: $error"),
                                        )
                                    }
                                }
                        } else if (continuation.isActive) {
                            continuation.resumeWithException(
                                IllegalStateException("HTTP ${response.status} for $url"),
                            )
                        }
                    }
                    .`catch` { error ->
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                IllegalStateException("Network error for $url: $error"),
                            )
                        }
                    }
                continuation.invokeOnCancellation { controller.abort() }
            }
        }
        if (body == null) {
            Result.failure(IllegalStateException("Timed out after ${timeoutMillis}ms calling $url"))
        } else {
            Result.success(body)
        }
    } catch (throwable: Throwable) {
        Result.failure(throwable)
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 6_000L
    }
}

/** Key-less Nominatim reverse-geocoding service for the browser. */
fun webReverseGeocodingService(): ReverseGeocodingService =
    NominatimReverseGeocodingService(WebHttpClient())

private external interface WasmAbortController : JsAny {
    val signal: JsAny
    fun abort()
}

private fun newAbortController(): WasmAbortController = js("new AbortController()")

private external interface WasmHeaders : JsAny {
    fun append(name: String, value: String)
}

private fun newJsHeaders(): WasmHeaders = js("new Headers()")

private fun createHeaders(headers: Map<String, String>): WasmHeaders =
    newJsHeaders().also { target ->
        headers.forEach { (name, value) -> target.append(name, value) }
    }

private fun buildRequestInit(headers: WasmHeaders, signal: JsAny): JsAny =
    js("({ method: 'GET', headers: headers, signal: signal })")

@JsName("fetch")
private external fun browserFetch(url: String, init: JsAny): WasmFetchPromise

private external interface WasmFetchPromise : JsAny {
    fun then(onFulfilled: (WasmFetchResponse) -> Unit): WasmFetchPromise
    fun `catch`(onRejected: (JsAny) -> Unit): WasmFetchPromise
}

private external interface WasmFetchResponse : JsAny {
    val ok: Boolean
    val status: Int
    fun text(): WasmTextPromise
}

private external interface WasmTextPromise : JsAny {
    fun then(onFulfilled: (String) -> Unit): WasmTextPromise
    fun `catch`(onRejected: (JsAny) -> Unit): WasmTextPromise
}
