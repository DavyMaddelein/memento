package com.memento.platform.web

import com.memento.domain.model.Coordinates
import com.memento.platform.contract.LocationProvider
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * [LocationProvider] backed by the browser Geolocation API.
 *
 * [hasPermission] always returns `true` because browsers surface the permission prompt on demand
 * when [getCurrentCoordinates] is called. Failures reported by the API are returned as a failed
 * [Result] rather than thrown.
 */
class WebGeolocationProvider : LocationProvider {

    override fun hasPermission(): Boolean = true

    override suspend fun getCurrentCoordinates(): Result<Coordinates> = try {
        val coordinates = suspendCancellableCoroutine { continuation ->
            browserNavigator.geolocation.getCurrentPosition(
                successCallback = { position ->
                    if (continuation.isActive) {
                        runCatching {
                            val coords = position.coords
                            Coordinates(
                                latitude = coords.latitude,
                                longitude = coords.longitude,
                                accuracyMeters = coords.accuracy.takeIf { it >= 0.0 },
                            )
                        }.fold(
                            onSuccess = { continuation.resume(it) },
                            onFailure = { continuation.resumeWithException(it) },
                        )
                    }
                },
                errorCallback = { error ->
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            IllegalStateException("Geolocation request failed: $error"),
                        )
                    }
                },
            )
        }
        Result.success(coordinates)
    } catch (throwable: Throwable) {
        Result.failure(throwable)
    }
}
