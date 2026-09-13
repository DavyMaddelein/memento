@file:OptIn(ExperimentalCoroutinesApi::class)

package com.memento.presentation

import com.memento.storage.memory.InMemoryMementoRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlacesViewModelTest {
    @Test
    fun initialLoadGroupsMementos() = runTest {
        val repository = InMemoryMementoRepository()
        repository.saveMementos(
            listOf(
                testMemento(id = "tokyo-1", brand = "Cafe A", city = "Tokyo"),
                testMemento(id = "tokyo-2", brand = "Cafe B", city = "Tokyo"),
                testMemento(id = "kyoto-1", brand = "Cafe C", city = "Kyoto"),
            ),
        )
        val viewModel = PlacesViewModel(repository, scope = newViewModelScope())

        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertFalse(state.isEmpty)
        assertEquals(2, state.groups.size)
        assertEquals(listOf("tokyo", "kyoto"), state.groups.map { it.key })
        assertEquals(2, state.groups.first { it.key == "tokyo" }.count)

        viewModel.dispose()
        advanceUntilIdle()
    }

    @Test
    fun addingMementoInNewCityUpdatesGroupsReactively() = runTest {
        val repository = InMemoryMementoRepository()
        repository.saveMemento(testMemento(id = "tokyo", brand = "Cafe A", city = "Tokyo"))
        val viewModel = PlacesViewModel(repository, scope = newViewModelScope())
        advanceUntilIdle()
        assertEquals(listOf("tokyo"), viewModel.state.value.groups.map { it.key })

        repository.saveMemento(testMemento(id = "osaka", brand = "Cafe B", city = "Osaka"))
        advanceUntilIdle()

        assertEquals(listOf("osaka", "tokyo"), viewModel.state.value.groups.map { it.key })

        repository.saveMemento(testMemento(id = "osaka-2", brand = "Cafe C", city = "Osaka"))
        advanceUntilIdle()

        val osaka = viewModel.state.value.groups.first { it.key == "osaka" }
        assertEquals(2, osaka.count)

        viewModel.dispose()
        advanceUntilIdle()
    }

    @Test
    fun emptyRepositoryReportsEmptyAfterLoad() = runTest {
        val repository = InMemoryMementoRepository()
        val viewModel = PlacesViewModel(repository, scope = newViewModelScope())

        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertTrue(state.isEmpty)
        assertTrue(state.groups.isEmpty())

        viewModel.dispose()
        advanceUntilIdle()
    }

    @Test
    fun disposeCancelsCollection() = runTest {
        val repository = InMemoryMementoRepository()
        repository.saveMemento(testMemento(id = "tokyo", brand = "Cafe A", city = "Tokyo"))
        val scope = newViewModelScope()
        val viewModel = PlacesViewModel(repository, scope = scope)
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.groups.size)

        viewModel.dispose()
        assertFalse(scope.isActive)

        repository.saveMemento(
            testMemento(
                id = "osaka",
                brand = "Cafe B",
                city = "Osaka",
                occurredAt = Instant.fromEpochMilliseconds(1),
            ),
        )
        advanceUntilIdle()

        assertEquals(listOf("tokyo"), viewModel.state.value.groups.map { it.key })
    }
}
