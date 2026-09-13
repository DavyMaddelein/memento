package com.memento.domain.usecase

import com.memento.domain.T1
import com.memento.domain.T2
import com.memento.domain.T3
import com.memento.domain.T4
import com.memento.domain.memento
import com.memento.domain.model.CollectionId
import com.memento.domain.model.Place
import com.memento.domain.model.Rating
import com.memento.domain.model.Tag
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class FilterMementosUseCaseTest {

    private val useCase = FilterMementosUseCase()

    private val alpha = memento(
        id = "alpha",
        title = "Alpha",
        place = Place("Cafe Alpha", brand = "Starbucks", city = "Tokyo"),
        rating = Rating(3),
        tags = listOf(Tag("coffee")),
        collectionIds = listOf(CollectionId("c1")),
        occurredAt = T1,
    )
    private val beta = memento(
        id = "beta",
        title = "Beta",
        place = Place("Cafe Beta", brand = "Local Roasters", city = "Osaka"),
        rating = Rating(5),
        tags = listOf(Tag("Tea")),
        collectionIds = listOf(CollectionId("c2")),
        occurredAt = T2,
    )
    private val gamma = memento(
        id = "gamma",
        title = "Gamma",
        place = Place("Cafe Gamma", brand = "Starbucks", city = "Kyoto"),
        rating = null,
        tags = listOf(Tag("coffee")),
        collectionIds = listOf(CollectionId("c1")),
        occurredAt = T3,
    )
    private val delta = memento(
        id = "delta",
        title = "delta",
        place = null,
        rating = Rating(1),
        tags = emptyList(),
        collectionIds = emptyList(),
        occurredAt = T4,
    )

    private val all = listOf(alpha, beta, gamma, delta)

    private fun ids(mementos: List<com.memento.domain.model.Memento>): List<String> =
        mementos.map { it.id.value }

    @Test
    fun defaultFilterReturnsAllMementosNewestFirst() {
        assertContentEquals(listOf("delta", "gamma", "beta", "alpha"), ids(useCase(all)))
    }

    @Test
    fun filterByCollectionId() {
        val result = useCase(all, MementoFilter(collectionId = CollectionId("c1")))

        assertContentEquals(listOf("gamma", "alpha"), ids(result))
    }

    @Test
    fun filterByTagIsCaseInsensitive() {
        val result = useCase(all, MementoFilter(tag = "COFFEE"))

        assertContentEquals(listOf("gamma", "alpha"), ids(result))
    }

    @Test
    fun blankTagDoesNotFilter() {
        val result = useCase(all, MementoFilter(tag = "   "))

        assertEquals(4, result.size)
    }

    @Test
    fun filterByMinRatingExcludesUnratedAndLower() {
        val result = useCase(all, MementoFilter(minRating = 4))

        assertContentEquals(listOf("beta"), ids(result))
    }

    @Test
    fun filterByBrandIsCaseInsensitive() {
        val result = useCase(all, MementoFilter(brand = "starbucks"))

        assertContentEquals(listOf("gamma", "alpha"), ids(result))
    }

    @Test
    fun filterFromInstantIsInclusive() {
        val result = useCase(all, MementoFilter(from = T2))

        assertContentEquals(listOf("delta", "gamma", "beta"), ids(result))
    }

    @Test
    fun filterToInstantIsInclusive() {
        val result = useCase(all, MementoFilter(to = T2))

        assertContentEquals(listOf("beta", "alpha"), ids(result))
    }

    @Test
    fun filterByInstantRange() {
        val result = useCase(all, MementoFilter(from = T2, to = T3))

        assertContentEquals(listOf("gamma", "beta"), ids(result))
    }

    @Test
    fun filterCombinesQueryWithOtherCriteria() {
        val result = useCase(all, MementoFilter(brand = "starbucks", query = "kyoto"))

        assertContentEquals(listOf("gamma"), ids(result))
    }

    @Test
    fun sortNewestFirst() {
        val result = useCase(all, MementoFilter(sort = MementoSort.NEWEST_FIRST))

        assertContentEquals(listOf("delta", "gamma", "beta", "alpha"), ids(result))
    }

    @Test
    fun sortOldestFirst() {
        val result = useCase(all, MementoFilter(sort = MementoSort.OLDEST_FIRST))

        assertContentEquals(listOf("alpha", "beta", "gamma", "delta"), ids(result))
    }

    @Test
    fun sortHighestRatedPutsUnratedLast() {
        val result = useCase(all, MementoFilter(sort = MementoSort.HIGHEST_RATED))

        assertContentEquals(listOf("beta", "alpha", "delta", "gamma"), ids(result))
    }

    @Test
    fun sortByTitleIsCaseInsensitiveAlphabetical() {
        val result = useCase(all, MementoFilter(sort = MementoSort.TITLE))

        assertContentEquals(listOf("alpha", "beta", "delta", "gamma"), ids(result))
    }
}
