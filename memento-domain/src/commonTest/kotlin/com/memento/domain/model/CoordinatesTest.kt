package com.memento.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class CoordinatesTest {

    @Test
    fun validCoordinatesAreAccepted() {
        val coordinates = Coordinates(latitude = 35.6812, longitude = 139.7671, accuracyMeters = 8.5)

        assertEquals(35.6812, coordinates.latitude)
        assertEquals(139.7671, coordinates.longitude)
        assertEquals(8.5, coordinates.accuracyMeters)
    }

    @Test
    fun accuracyIsOptionalAndDefaultsToNull() {
        val coordinates = Coordinates(latitude = 0.0, longitude = 0.0)

        assertNull(coordinates.accuracyMeters)
    }

    @Test
    fun latitudeBoundsAreInclusive() {
        assertEquals(90.0, Coordinates(90.0, 0.0).latitude)
        assertEquals(-90.0, Coordinates(-90.0, 0.0).latitude)
    }

    @Test
    fun longitudeBoundsAreInclusive() {
        assertEquals(180.0, Coordinates(0.0, 180.0).longitude)
        assertEquals(-180.0, Coordinates(0.0, -180.0).longitude)
    }

    @Test
    fun zeroAccuracyIsAllowed() {
        assertEquals(0.0, Coordinates(1.0, 2.0, 0.0).accuracyMeters)
    }

    @Test
    fun latitudeAboveRangeThrows() {
        assertFailsWith<IllegalArgumentException> { Coordinates(91.0, 0.0) }
    }

    @Test
    fun latitudeBelowRangeThrows() {
        assertFailsWith<IllegalArgumentException> { Coordinates(-91.0, 0.0) }
    }

    @Test
    fun longitudeAboveRangeThrows() {
        assertFailsWith<IllegalArgumentException> { Coordinates(0.0, 181.0) }
    }

    @Test
    fun longitudeBelowRangeThrows() {
        assertFailsWith<IllegalArgumentException> { Coordinates(0.0, -181.0) }
    }

    @Test
    fun negativeAccuracyThrows() {
        assertFailsWith<IllegalArgumentException> { Coordinates(0.0, 0.0, accuracyMeters = -0.1) }
    }
}
