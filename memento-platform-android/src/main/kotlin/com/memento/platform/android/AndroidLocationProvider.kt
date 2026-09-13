package com.memento.platform.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.memento.domain.model.Coordinates
import com.memento.platform.contract.LocationProvider
import java.util.concurrent.TimeoutException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [LocationProvider] backed by [LocationManager].
 *
 * The provider first tries the platform's last known fix. When none is available it requests a
 * single update from the GPS and network providers and resumes with the first fix, or fails after
 * [timeoutMillis] (15 seconds by default) or on [SecurityException].
 */
class AndroidLocationProvider(
    private val context: Context,
    private val permissionChecker: () -> Boolean = {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
    },
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) : LocationProvider {

    override fun hasPermission(): Boolean = permissionChecker()

    override suspend fun getCurrentCoordinates(): Result<Coordinates> {
        if (!permissionChecker()) {
            return Result.failure(SecurityException("Location permission is not granted"))
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return Result.failure(IllegalStateException("LocationManager is not available"))

        val lastKnown = runCatching { lastKnownLocation(locationManager) }.getOrNull()
        if (lastKnown != null) {
            return Result.success(lastKnown.toCoordinates())
        }

        return try {
            val coordinates = withTimeoutOrNull(timeoutMillis) { awaitSingleLocation(locationManager) }
            if (coordinates == null) {
                Result.failure(
                    TimeoutException("Timed out after ${timeoutMillis}ms waiting for a location fix"),
                )
            } else {
                Result.success(coordinates)
            }
        } catch (throwable: Throwable) {
            Result.failure(throwable)
        }
    }

    private fun lastKnownLocation(locationManager: LocationManager): Location? =
        locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

    private suspend fun awaitSingleLocation(locationManager: LocationManager): Coordinates =
        suspendCancellableCoroutine { continuation ->
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    locationManager.removeUpdates(this)
                    if (continuation.isActive) {
                        continuation.resume(location.toCoordinates())
                    }
                }
            }
            continuation.invokeOnCancellation { locationManager.removeUpdates(listener) }

            for (provider in PROVIDERS) {
                try {
                    locationManager.requestLocationUpdates(
                        provider,
                        0L,
                        0f,
                        listener,
                        Looper.getMainLooper(),
                    )
                } catch (securityException: SecurityException) {
                    locationManager.removeUpdates(listener)
                    if (continuation.isActive) {
                        continuation.resumeWithException(securityException)
                    }
                    return@suspendCancellableCoroutine
                } catch (ignored: IllegalArgumentException) {
                    // Provider is not present on this device; try the next one.
                }
            }
        }

    private fun Location.toCoordinates(): Coordinates = Coordinates(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = if (hasAccuracy() && accuracy >= 0f) accuracy.toDouble() else null,
    )

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 15_000L
        val PROVIDERS = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
    }
}
