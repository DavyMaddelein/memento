@file:OptIn(kotlinx.cinterop.BetaInteropApi::class)

package com.memento.platform.ios

import com.memento.platform.contract.NominatimReverseGeocodingService
import com.memento.platform.contract.PlatformHttpClient
import com.memento.platform.contract.ReverseGeocodingService
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.create
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue

/**
 * [PlatformHttpClient] backed by [NSURLSession].
 *
 * Any transport problem (unreachable host, timeout, non-2xx status, undecodable body) is returned
 * as a failed [Result] rather than thrown, so reverse geocoding degrades gracefully offline.
 */
class IosHttpClient(
    private val timeoutSeconds: Double = DEFAULT_TIMEOUT_SECONDS,
) : PlatformHttpClient {

    override suspend fun get(url: String, headers: Map<String, String>): Result<String> = try {
        val nsUrl = NSURL.URLWithString(url)
            ?: return Result.failure(IllegalArgumentException("Invalid URL: $url"))
        val request = NSMutableURLRequest(uRL = nsUrl)
        request.setHTTPMethod("GET")
        request.setTimeoutInterval(timeoutSeconds)
        headers.forEach { (name, value) -> request.setValue(value, forHTTPHeaderField = name) }
        val session = NSURLSession.sessionWithConfiguration(
            NSURLSessionConfiguration.defaultSessionConfiguration,
        )
        val body = suspendCancellableCoroutine { continuation ->
            val task = session.dataTaskWithRequest(request) { data, response, error ->
                val http = response as? NSHTTPURLResponse
                when {
                    error != null -> if (continuation.isActive) {
                        continuation.resumeWithException(
                            IllegalStateException("Network error for $url: ${error.localizedDescription}"),
                        )
                    }

                    http != null && http.statusCode.toInt() !in 200..299 -> if (continuation.isActive) {
                        continuation.resumeWithException(
                            IllegalStateException("HTTP ${http.statusCode} for $url"),
                        )
                    }

                    else -> {
                        val text = data?.let { bytes ->
                            NSString.create(data = bytes, encoding = NSUTF8StringEncoding) as String?
                        }
                        if (continuation.isActive) {
                            if (text == null) {
                                continuation.resumeWithException(
                                    IllegalStateException("Undecodable or empty body for $url"),
                                )
                            } else {
                                continuation.resume(text)
                            }
                        }
                    }
                }
            }
            continuation.invokeOnCancellation { task.cancel() }
            task.resume()
        }
        Result.success(body)
    } catch (t: Throwable) {
        Result.failure(t)
    }

    private companion object {
        const val DEFAULT_TIMEOUT_SECONDS = 6.0
    }
}

/** Key-less Nominatim reverse-geocoding service for iOS. */
fun iosReverseGeocodingService(): ReverseGeocodingService =
    NominatimReverseGeocodingService(IosHttpClient())
