package com.memento.domain.places

import com.memento.domain.T1
import com.memento.domain.T2
import com.memento.domain.T3
import com.memento.domain.memento
import com.memento.domain.model.Place
import com.memento.domain.model.Rating
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaceGroupsTest {

    private fun ids(group: PlaceGroup): List<String> = group.mementos.map { it.id.value }

    @Test
    fun groupsByCityCaseInsensitively() {
        val groups = listOf(
            memento(id = "tokyo-upper", place = Place("Cafe A", city = "Tokyo", country = "Japan")),
            memento(id = "tokyo-lower", place = Place("Cafe B", city = "tokyo", country = "Japan")),
            memento(id = "kyoto", place = Place("Cafe C", city = "Kyoto", country = "Japan")),
        ).groupMementosByPlace()

        assertEquals(2, groups.size)

        val tokyo = groups.first { it.key == "tokyo" }
        assertEquals("Tokyo", tokyo.title)
        assertEquals(2, tokyo.count)

        val kyoto = groups.first { it.key == "kyoto" }
        assertEquals("Kyoto", kyoto.title)
        assertEquals(1, kyoto.count)
    }

    @Test
    fun fallsBackToPlaceNameWhenCityBlank() {
        val groups = listOf(
            memento(id = "a", place = Place("Onigiri Bongo", city = "   ", country = "Japan")),
            memento(id = "b", place = Place("Onigiri Bongo", country = "Japan")),
        ).groupMementosByPlace()

        val group = groups.single()
        assertEquals("onigiri bongo", group.key)
        assertEquals("Onigiri Bongo", group.title)
        assertEquals("Japan", group.subtitle)
        assertEquals(2, group.count)
    }

    @Test
    fun nameGroupIncludesCityWhenPresent() {
        val groups = listOf(
            memento(id = "a", place = Place("Bongo")),
        ).groupMementosByPlace()

        val group = groups.single()
        assertEquals("bongo", group.key)
        assertEquals("Bongo", group.title)
        assertNull(group.subtitle)
    }

    @Test
    fun nullPlaceFallsIntoUnknownGroupAlwaysLast() {
        val groups = listOf(
            memento(id = "unknown-1", place = null),
            memento(id = "unknown-2", place = null),
            memento(id = "tokyo", place = Place("Cafe", city = "Tokyo")),
        ).groupMementosByPlace()

        val unknown = groups.last()
        assertEquals(UNKNOWN_PLACE_KEY, unknown.key)
        assertEquals("Unknown location", unknown.title)
        assertNull(unknown.subtitle)
        assertEquals(2, unknown.count)
        assertEquals(listOf("tokyo"), groups.dropLast(1).map { it.key })
    }

    @Test
    fun countsAverageRatingAndLatestOccurredAt() {
        val groups = listOf(
            memento(id = "newest", place = Place("A", city = "Tokyo"), rating = Rating(5), occurredAt = T3),
            memento(id = "middle", place = Place("B", city = "Tokyo"), rating = Rating(3), occurredAt = T2),
            memento(id = "oldest", place = Place("C", city = "Tokyo"), occurredAt = T1),
        ).groupMementosByPlace()

        val group = groups.single()
        assertEquals(3, group.count)
        assertEquals(4.0, group.averageRating)
        assertEquals(T3, group.latestOccurredAt)
    }

    @Test
    fun averageRatingIsNullWhenNoMementoIsRated() {
        val groups = listOf(
            memento(id = "a", place = Place("A", city = "Tokyo")),
        ).groupMementosByPlace()

        assertNull(groups.single().averageRating)
    }

    @Test
    fun ordersGroupsByCountThenTitleAndMementosNewestFirst() {
        val groups = listOf(
            memento(id = "osaka", place = Place("A", city = "Osaka"), occurredAt = T1),
            memento(id = "tokyo-old", place = Place("B", city = "Tokyo"), occurredAt = T1),
            memento(id = "tokyo-new", place = Place("C", city = "Tokyo"), occurredAt = T3),
            memento(id = "kyoto-old", place = Place("D", city = "Kyoto"), occurredAt = T1),
            memento(id = "kyoto-new", place = Place("E", city = "Kyoto"), occurredAt = T2),
        ).groupMementosByPlace()

        assertEquals(listOf("kyoto", "tokyo", "osaka"), groups.map { it.key })
        assertEquals(listOf("tokyo-new", "tokyo-old"), ids(groups.first { it.key == "tokyo" }))
    }

    @Test
    fun unknownGroupStaysLastEvenWhenItHasTheMostMementos() {
        val groups = listOf(
            memento(id = "tokyo", place = Place("A", city = "Tokyo")),
            memento(id = "unknown-1", place = null),
            memento(id = "unknown-2", place = null),
        ).groupMementosByPlace()

        assertEquals(listOf("tokyo", UNKNOWN_PLACE_KEY), groups.map { it.key })
    }

    @Test
    fun subtitlesListDistinctCountries() {
        val groups = listOf(
            memento(id = "jp-1", place = Place("A", city = "Tokyo", country = "Japan")),
            memento(id = "jp-2", place = Place("B", city = "Tokyo", country = "Japan")),
            memento(id = "fr", place = Place("C", city = "Tokyo", country = "France")),
        ).groupMementosByPlace()

        assertEquals("Japan, France", groups.single().subtitle)
    }
}
