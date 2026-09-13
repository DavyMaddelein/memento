package com.memento.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PlaceAndTagTest {

    @Test
    fun validPlaceIsAccepted() {
        val place = Place(
            name = "Onigiri Bongo",
            brand = "Bongo",
            neighborhood = "Toshima",
            city = "Tokyo",
            country = "Japan",
        )

        assertEquals("Onigiri Bongo", place.name)
        assertEquals("Bongo", place.brand)
        assertEquals("Tokyo", place.city)
    }

    @Test
    fun optionalPlaceMetadataDefaultsToNull() {
        val place = Place("Nameless stall")

        assertEquals(null, place.brand)
        assertEquals(null, place.neighborhood)
        assertEquals(null, place.city)
        assertEquals(null, place.country)
    }

    @Test
    fun blankPlaceNameThrows() {
        assertFailsWith<IllegalArgumentException> { Place("") }
    }

    @Test
    fun whitespacePlaceNameThrows() {
        assertFailsWith<IllegalArgumentException> { Place("   ") }
    }

    @Test
    fun validTagIsAccepted() {
        assertEquals("coffee", Tag("coffee").value)
    }

    @Test
    fun blankTagThrows() {
        assertFailsWith<IllegalArgumentException> { Tag("") }
    }

    @Test
    fun whitespaceTagThrows() {
        assertFailsWith<IllegalArgumentException> { Tag(" \t ") }
    }

    @Test
    fun tagAtMaximumLengthIsAccepted() {
        val value = "a".repeat(Tag.MAX_LENGTH)

        assertEquals(value, Tag(value).value)
    }

    @Test
    fun tagOverMaximumLengthThrows() {
        assertFailsWith<IllegalArgumentException> { Tag("a".repeat(Tag.MAX_LENGTH + 1)) }
    }
}
