package com.memento.platform.contract

import com.memento.domain.model.Coordinates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class NominatimReverseGeocodingServiceTest {

    private val shibuya = Coordinates(latitude = 35.658034, longitude = 139.701636, accuracyMeters = 5.0)

    private val sampleBody =
        """{"place_id":266366356,"name":"渋谷駅前","display_name":"渋谷駅前, 渋谷センター街, 道玄坂二丁目, 道玄坂, 渋谷区, 東京都, 150-0043, 日本","address":{"junction":"渋谷駅前","road":"渋谷センター街","neighbourhood":"道玄坂二丁目","quarter":"道玄坂","city":"渋谷区","postcode":"150-0043","country":"日本","country_code":"jp"}}"""

    @Test
    fun mapsAllFieldsFromRealSample() = runTest {
        val client = RecordingHttpClient(Result.success(sampleBody))
        val service = NominatimReverseGeocodingService(client)

        val place = service.reverseGeocode(shibuya).getOrThrow()

        assertEquals("渋谷駅前", place.name)
        assertEquals("道玄坂二丁目", place.neighborhood)
        assertEquals("渋谷区", place.city)
        assertEquals("日本", place.country)
        assertEquals(
            "渋谷駅前, 渋谷センター街, 道玄坂二丁目, 道玄坂, 渋谷区, 東京都, 150-0043, 日本",
            place.displayName,
        )
    }

    @Test
    fun buildsTheExpectedRequestUrlAndHeaders() = runTest {
        val client = RecordingHttpClient(Result.success("{}"))
        val service = NominatimReverseGeocodingService(client)

        service.reverseGeocode(shibuya)

        val url = client.lastUrl.orEmpty()
        val headers = client.lastHeaders
        assertTrue(url.startsWith("https://nominatim.openstreetmap.org/reverse?"), url)
        assertTrue(url.contains("format=jsonv2"), url)
        assertTrue(url.contains("lat=35.658034"), url)
        assertTrue(url.contains("lon=139.701636"), url)
        assertTrue(url.contains("zoom=18"), url)
        assertTrue(url.contains("addressdetails=1"), url)
        assertTrue(headers["User-Agent"]?.startsWith("memento-kmp/") == true, headers.toString())
        assertEquals("application/json", headers["Accept"])
    }

    @Test
    fun transportFailureReturnsFailure() = runTest {
        val client = RecordingHttpClient(Result.failure(IllegalStateException("offline")))
        val service = NominatimReverseGeocodingService(client)

        val result = service.reverseGeocode(shibuya)

        assertTrue(result.isFailure, "expected failure but got $result")
    }

    @Test
    fun malformedJsonReturnsFailure() = runTest {
        val client = RecordingHttpClient(Result.success("{ this is not json"))
        val service = NominatimReverseGeocodingService(client)

        val result = service.reverseGeocode(shibuya)

        assertTrue(result.isFailure, "expected failure but got $result")
    }

    @Test
    fun responseWithoutAddressOrNameReturnsFailure() = runTest {
        val body = """{"place_id":266366356,"licence":"ODbL","osm_type":"node","osm_id":1}"""
        val client = RecordingHttpClient(Result.success(body))
        val service = NominatimReverseGeocodingService(client)

        val result = service.reverseGeocode(shibuya)

        assertTrue(result.isFailure, "expected failure but got $result")
    }

    @Test
    fun emptyObjectReturnsFailure() = runTest {
        val client = RecordingHttpClient(Result.success("{}"))
        val service = NominatimReverseGeocodingService(client)

        val result = service.reverseGeocode(shibuya)

        assertTrue(result.isFailure, "expected failure but got $result")
    }

    @Test
    fun partialResponseSucceedsWithAvailableFields() = runTest {
        val body = """{"display_name":"Somewhere, Earth","address":{"country":"Japan"}}"""
        val client = RecordingHttpClient(Result.success(body))
        val service = NominatimReverseGeocodingService(client)

        val place = service.reverseGeocode(shibuya).getOrThrow()

        assertEquals("Somewhere, Earth", place.displayName)
        assertEquals("Japan", place.country)
        assertEquals(null, place.name)
        assertEquals(null, place.city)
    }

    @Test
    fun neverThrowsWhenTheTransportThrows() = runTest {
        val throwingClient = object : PlatformHttpClient {
            override suspend fun get(url: String, headers: Map<String, String>): Result<String> =
                throw IllegalStateException("boom")
        }
        val service = NominatimReverseGeocodingService(throwingClient)

        val result = service.reverseGeocode(shibuya)

        assertTrue(result.isFailure, "expected failure but got $result")
    }

    @Test
    fun fakeServiceCountsCallsAndReturnsConfiguredResult() = runTest {
        val fake = com.memento.platform.contract.fakes.FakeReverseGeocodingService()

        val place = fake.reverseGeocode(shibuya).getOrThrow()

        assertEquals(1, fake.callCount)
        assertEquals("Shibuya Station", place.name)
        assertEquals("Dogenzaka", place.neighborhood)
        assertEquals("Shibuya", place.city)
        assertEquals("Japan", place.country)
    }

    private class RecordingHttpClient(
        private val response: Result<String>,
    ) : PlatformHttpClient {
        var lastUrl: String? = null
            private set
        var lastHeaders: Map<String, String> = emptyMap()
            private set

        override suspend fun get(url: String, headers: Map<String, String>): Result<String> {
            lastUrl = url
            lastHeaders = headers
            return response
        }
    }
}
