package com.memento.platform.contract

import com.memento.domain.model.Coordinates
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Place details resolved from a pair of coordinates. Every field is optional because a
 * reverse-geocoding provider may return only partial information.
 */
data class ResolvedPlace(
    val name: String? = null,
    val neighborhood: String? = null,
    val city: String? = null,
    val country: String? = null,
    val displayName: String? = null,
)

/**
 * Coordinates -> place details capability.
 *
 * Implementations must never throw for expected failures (offline, timeout, CORS, malformed
 * payload); they return a failed [Result] instead so coordinates stay usable.
 */
interface ReverseGeocodingService {
    suspend fun reverseGeocode(coordinates: Coordinates): Result<ResolvedPlace>
}

/**
 * Minimal HTTP GET seam so [NominatimReverseGeocodingService] is transport-agnostic and
 * unit-testable. Implementations must return a failed [Result] instead of throwing.
 */
interface PlatformHttpClient {
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): Result<String>
}

/**
 * [ReverseGeocodingService] backed by the free, key-less OpenStreetMap Nominatim API.
 *
 * The service degrades gracefully: a transport failure, timeout, non-2xx status, unparseable
 * body, or a body with neither address nor name yields a failed [Result] rather than throwing.
 * Success carries whatever subset of fields the provider returned.
 */
class NominatimReverseGeocodingService(
    private val http: PlatformHttpClient,
    private val userAgent: String = DEFAULT_USER_AGENT,
    private val endpoint: String = DEFAULT_ENDPOINT,
) : ReverseGeocodingService {

    override suspend fun reverseGeocode(coordinates: Coordinates): Result<ResolvedPlace> = runCatching {
        val body = http.get(
            url = buildUrl(coordinates),
            headers = mapOf("User-Agent" to userAgent, "Accept" to ACCEPT),
        ).getOrThrow()

        json.decodeFromString<NominatimResponse>(body).toResolvedPlace()
    }

    private fun buildUrl(coordinates: Coordinates): String {
        val separator = if (endpoint.contains('?')) '&' else '?'
        return buildString {
            append(endpoint)
            append(separator)
            append("format=jsonv2")
            append("&lat=").append(coordinates.latitude)
            append("&lon=").append(coordinates.longitude)
            append("&zoom=18")
            append("&addressdetails=1")
        }
    }

    private fun NominatimResponse.toResolvedPlace(): ResolvedPlace {
        val address = address
        val resolved = ResolvedPlace(
            name = firstNonBlank(
                name,
                address?.amenity,
                address?.shop,
                address?.tourism,
                address?.leisure,
                address?.office,
                address?.building,
                address?.name,
                address?.neighbourhood,
                address?.suburb,
                address?.city,
                address?.town,
                address?.village,
                address?.road,
            ),
            neighborhood = firstNonBlank(address?.neighbourhood, address?.suburb, address?.quarter),
            city = firstNonBlank(
                address?.city,
                address?.town,
                address?.village,
                address?.municipality,
                address?.county,
            ),
            country = address?.country,
            displayName = displayName,
        )
        if (address == null && resolved.name == null && resolved.displayName == null) {
            throw IllegalArgumentException("Nominatim response contained no address or name")
        }
        return resolved
    }

    private fun firstNonBlank(vararg candidates: String?): String? =
        candidates.firstOrNull { !it.isNullOrBlank() }

    private companion object {
        const val DEFAULT_USER_AGENT = "memento-kmp/0.1 (+https://github.com/DavyMaddelein/memento)"
        const val DEFAULT_ENDPOINT = "https://nominatim.openstreetmap.org/reverse"
        const val ACCEPT = "application/json"

        val json = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
private data class NominatimResponse(
    val name: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    val address: NominatimAddress? = null,
)

@Serializable
private data class NominatimAddress(
    val name: String? = null,
    val junction: String? = null,
    val road: String? = null,
    val amenity: String? = null,
    val shop: String? = null,
    val tourism: String? = null,
    val leisure: String? = null,
    val office: String? = null,
    val building: String? = null,
    @SerialName("house_number") val houseNumber: String? = null,
    val neighbourhood: String? = null,
    val suburb: String? = null,
    val quarter: String? = null,
    val city: String? = null,
    val town: String? = null,
    val village: String? = null,
    val municipality: String? = null,
    val county: String? = null,
    val country: String? = null,
)
