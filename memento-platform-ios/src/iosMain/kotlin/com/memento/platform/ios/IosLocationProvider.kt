@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.memento.platform.ios

import com.memento.domain.model.Coordinates
import com.memento.platform.contract.LocationProvider
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.cinterop.useContents
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.Foundation.NSError
import platform.darwin.NSObject

/**
 * [LocationProvider] backed by [CLLocationManager].
 *
 * Requests a single fix (after asking for when-in-use authorization when it has not been granted
 * yet) and resumes with the first location, or fails after [timeoutMillis] (15s by default).
 */
class IosLocationProvider(
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) : LocationProvider {

    private val manager = CLLocationManager()

    /** Strong reference: `CLLocationManager.delegate` is weak, so the delegate must be retained. */
    private var activeDelegate: NSObject? = null

    override fun hasPermission(): Boolean = when (manager.authorizationStatus) {
        kCLAuthorizationStatusAuthorizedAlways,
        kCLAuthorizationStatusAuthorizedWhenInUse,
        -> true

        else -> false
    }

    override suspend fun getCurrentCoordinates(): Result<Coordinates> {
        if (!hasPermission()) {
            manager.requestWhenInUseAuthorization()
        }
        manager.desiredAccuracy = DESIRED_ACCURACY_METERS

        return try {
            val coordinates = withTimeoutOrNull(timeoutMillis) { awaitLocation() }
            if (coordinates == null) {
                Result.failure(
                    IllegalStateException("Timed out after ${timeoutMillis}ms waiting for a location fix"),
                )
            } else {
                Result.success(coordinates)
            }
        } catch (throwable: Throwable) {
            Result.failure(throwable)
        }
    }

    private suspend fun awaitLocation(): Coordinates = suspendCancellableCoroutine { continuation ->
        val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
            override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
                val location = didUpdateLocations.lastOrNull() as? CLLocation ?: return
                detach()
                if (continuation.isActive) {
                    continuation.resume(location.toCoordinates())
                }
            }

            override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
                detach()
                if (continuation.isActive) {
                    continuation.resumeWithException(
                        IllegalStateException("Location lookup failed: ${didFailWithError.localizedDescription}"),
                    )
                }
            }

            private fun detach() {
                manager.delegate = null
                activeDelegate = null
            }
        }
        activeDelegate = delegate
        manager.delegate = delegate
        manager.requestLocation()
        continuation.invokeOnCancellation {
            manager.delegate = null
            activeDelegate = null
        }
    }

    private fun CLLocation.toCoordinates(): Coordinates {
        val latitude = coordinate.useContents { latitude }
        val longitude = coordinate.useContents { longitude }
        return Coordinates(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = horizontalAccuracy.takeIf { it >= 0.0 },
        )
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 15_000L
        const val DESIRED_ACCURACY_METERS = 100.0
    }
}
