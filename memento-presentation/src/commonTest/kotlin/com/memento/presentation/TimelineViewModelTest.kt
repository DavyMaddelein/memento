@file:OptIn(ExperimentalCoroutinesApi::class)

package com.memento.presentation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.memento.domain.model.CollectionId
import com.memento.domain.model.MementoId
import com.memento.domain.usecase.MementoSort
import com.memento.storage.memory.InMemoryMementoRepository
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TimelineViewModelTest {
    @Test
    fun initialLoadReflectsRepository() = runTest {
        val repository = InMemoryMementoRepository()
        repository.saveMemento(
            testMemento(
                id = "a",
                title = "Ramen",
                tags = listOf("food"),
                occurredAt = Instant.fromEpochMilliseconds(1),
            ),
        )
        repository.saveMemento(
            testMemento(
                id = "b",
                title = "Coffee",
                tags = listOf("drink"),
                occurredAt = Instant.fromEpochMilliseconds(2),
            ),
        )
        val viewModel = TimelineViewModel(repository, scope = newViewModelScope())

        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(2, state.mementos.size)
        assertFalse(state.isLoading)
        assertFalse(state.isEmpty)
        assertEquals(listOf("drink", "food"), state.allTags)

        viewModel.dispose()
        advanceUntilIdle()
    }

    @Test
    fun repositoryEmissionUpdatesState() = runTest {
        val repository = InMemoryMementoRepository()
        val viewModel = TimelineViewModel(repository, scope = newViewModelScope())
        advanceUntilIdle()
        assertTrue(viewModel.state.value.mementos.isEmpty())

        repository.saveMemento(testMemento(id = "a", title = "A"))
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.mementos.size)
        assertEquals("a", viewModel.state.value.mementos.single().id.value)

        viewModel.dispose()
        advanceUntilIdle()
    }

    @Test
    fun searchQueryFiltersMementos() = runTest {
        val repository = InMemoryMementoRepository()
        repository.saveMementos(
            listOf(
                testMemento(id = "ramen", title = "Tonkotsu Ramen"),
                testMemento(id = "coffee", title = "Canned Coffee"),
            ),
        )
        val viewModel = TimelineViewModel(repository, scope = newViewModelScope())
        advanceUntilIdle()

        viewModel.onSearchQueryChanged("ramen")
        advanceUntilIdle()

        assertEquals(listOf("ramen"), viewModel.state.value.mementos.map { it.id.value })

        viewModel.dispose()
        advanceUntilIdle()
    }

    @Test
    fun tagToggleFiltersMementos() = runTest {
        val repository = InMemoryMementoRepository()
        repository.saveMementos(
            listOf(
                testMemento(id = "a", tags = listOf("coffee")),
                testMemento(id = "b", tags = listOf("tea")),
            ),
        )
        val viewModel = TimelineViewModel(repository, scope = newViewModelScope())
        advanceUntilIdle()

        viewModel.onFilterTagToggled("coffee")
        advanceUntilIdle()
        assertEquals(listOf("a"), viewModel.state.value.mementos.map { it.id.value })

        viewModel.onFilterTagToggled("coffee")
        advanceUntilIdle()
        assertEquals(2, viewModel.state.value.mementos.size)

        viewModel.dispose()
        advanceUntilIdle()
    }

    @Test
    fun collectionSelectionFiltersMementos() = runTest {
        val collectionId = CollectionId("c1")
        val repository = InMemoryMementoRepository()
        repository.saveMementos(
            listOf(
                testMemento(id = "a").copy(collectionIds = listOf(collectionId)),
                testMemento(id = "b"),
            ),
        )
        val viewModel = TimelineViewModel(repository, scope = newViewModelScope())
        advanceUntilIdle()

        viewModel.onCollectionSelected(collectionId)
        advanceUntilIdle()

        assertEquals(listOf("a"), viewModel.state.value.mementos.map { it.id.value })

        viewModel.dispose()
        advanceUntilIdle()
    }

    @Test
    fun sortOrderChangesMementoOrder() = runTest {
        val repository = InMemoryMementoRepository()
        repository.saveMementos(
            listOf(
                testMemento(id = "old", occurredAt = Instant.fromEpochMilliseconds(1)),
                testMemento(id = "new", occurredAt = Instant.fromEpochMilliseconds(2)),
            ),
        )
        val viewModel = TimelineViewModel(repository, scope = newViewModelScope())
        advanceUntilIdle()

        assertEquals("new", viewModel.state.value.mementos.first().id.value)

        viewModel.onSortOrderSelected(MementoSort.OLDEST_FIRST)
        advanceUntilIdle()

        assertEquals("old", viewModel.state.value.mementos.first().id.value)

        viewModel.dispose()
        advanceUntilIdle()
    }

    @Test
    fun deleteRemovesMemento() = runTest {
        val repository = InMemoryMementoRepository()
        repository.saveMemento(testMemento(id = "a"))
        val viewModel = TimelineViewModel(repository, scope = newViewModelScope())
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.mementos.size)

        viewModel.onDeleteMemento(MementoId("a"))
        advanceUntilIdle()

        assertTrue(viewModel.state.value.mementos.isEmpty())
        assertTrue(viewModel.state.value.isEmpty)

        viewModel.dispose()
        advanceUntilIdle()
    }
}
