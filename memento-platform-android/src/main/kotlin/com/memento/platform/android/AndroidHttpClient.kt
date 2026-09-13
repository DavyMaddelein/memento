package com.memento.platform.android

import com.memento.platform.contract.NominatimReverseGeocodingService
import com.memento.platform.contract.PlatformHttpClient
import com.memento.platform.contract.ReverseGeocodingService
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [PlatformHttpClient] backed by [HttpURLConnection], executed on [Dispatchers.IO].
 *
 * Any IO problem (unreachable host, timeout, malformed URL, non-2xx status) is returned as a
 * failed [Result] instead of being thrown, so reverse geocoding degrades gracefully.
 */
class AndroidHttpClient(
    private val connectTimeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
    private val readTimeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
) : PlatformHttpClient {

    override suspend fun get(url: String, headers: Map<String, String>): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val connection = URL(url).openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "GET"
                    connection.connectTimeout = connectTimeoutMillis
                    connection.readTimeout = readTimeoutMillis
                    headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
                    val status = connection.responseCode
                    if (status in 200..299) {
                        connection.inputStream.bufferedReader().use { it.readText() }
                    } else {
                        throw IOException("HTTP $status for $url")
                    }
                } finally {
                    connection.disconnect()
                }
            }
        }

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 6_000
    }
}

/** Key-less Nominatim reverse-geocoding service for Android. */
fun androidReverseGeocodingService(): ReverseGeocodingService =
    NominatimReverseGeocodingService(AndroidHttpClient())
