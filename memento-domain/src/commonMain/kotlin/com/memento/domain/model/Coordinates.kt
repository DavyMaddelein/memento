package com.memento.domain.model

import kotlinx.serialization.Serializable

/**
 * Geographic coordinate with an optional horizontal accuracy in meters.
 *
 * Invariants:
 * - [latitude] must lie in [-90, 90]
 * - [longitude] must lie in [-180, 180]
 * - [accuracyMeters] must be non-negative when present
 */
@Serializable
data class Coordinates(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double? = null,
) {
    init {
        require(latitude in -90.0..90.0) { "latitude must be within [-90, 90], was $latitude" }
        require(longitude in -180.0..180.0) { "longitude must be within [-180, 180], was $longitude" }
        require(accuracyMeters == null || accuracyMeters >= 0.0) {
            "accuracyMeters must be non-negative, was $accuracyMeters"
        }
    }
}
