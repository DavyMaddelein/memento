package com.memento.domain.usecase

import com.memento.domain.memento
import com.memento.domain.model.Place
import com.memento.domain.model.Tag
import com.memento.domain.model.TastingNotes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchMementosUseCaseTest {

    private val useCase = SearchMementosUseCase()

    private val byTitle = memento(id = "title", title = "Rainbow Mountain Latte")
    private val byReflection = memento(id = "reflection", reflection = "That trip was unforgettable")
    private val byTasting = memento(
        id = "tasting",
        tastingNotes = TastingNotes(text = "Rich caramel finish"),
    )
    private val byPlaceName = memento(id = "place-name", place = Place("Onigiri Bongo"))
    private val byPlaceBrand = memento(id = "place-brand", place = Place("Corner Shop", brand = "Suntory"))
    private val byPlaceCity = memento(id = "place-city", place = Place("Corner Shop", city = "Kyoto"))
    private val byTag = memento(id = "tag", tags = listOf(Tag("ramen")))

    private val all = listOf(
        byTitle,
        byReflection,
        byTasting,
        byPlaceName,
        byPlaceBrand,
        byPlaceCity,
        byTag,
    )

    private fun ids(mementos: List<com.memento.domain.model.Memento>): List<String> =
        mementos.map { it.id.value }

    @Test
    fun matchesTitle() {
        assertContentEquals(listOf("title"), ids(useCase(all, "Rainbow")))
    }

    @Test
    fun matchesReflection() {
        assertContentEquals(listOf("reflection"), ids(useCase(all, "unforgettable")))
    }

    @Test
    fun matchesTastingNotes() {
        assertContentEquals(listOf("tasting"), ids(useCase(all, "caramel")))
    }

    @Test
    fun matchesPlaceName() {
        assertContentEquals(listOf("place-name"), ids(useCase(all, "Bongo")))
    }

    @Test
    fun matchesPlaceBrand() {
        assertContentEquals(listOf("place-brand"), ids(useCase(all, "suntory")))
    }

    @Test
    fun matchesPlaceCity() {
        assertContentEquals(listOf("place-city"), ids(useCase(all, "Kyoto")))
    }

    @Test
    fun matchesTag() {
        assertContentEquals(listOf("tag"), ids(useCase(all, "Ramen")))
    }

    @Test
    fun searchIsCaseInsensitive() {
        assertContentEquals(listOf("title"), ids(useCase(all, "rAiNbOw mOuNtAiN")))
    }

    @Test
    fun emptyQueryReturnsInputUnchanged() {
        assertContentEquals(ids(all), ids(useCase(all, "")))
    }

    @Test
    fun blankQueryReturnsInputUnchanged() {
        assertContentEquals(ids(all), ids(useCase(all, "   ")))
    }

    @Test
    fun noMatchReturnsEmptyList() {
        assertTrue(useCase(all, "nonexistent-needle").isEmpty())
    }

    @Test
    fun resultPreservesInputOrder() {
        val first = memento(id = "first", title = "Coffee one")
        val second = memento(id = "second", title = "Coffee two")

        assertEquals(listOf("first", "second"), ids(useCase(listOf(first, second), "coffee")))
    }
}
