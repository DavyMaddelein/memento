@file:OptIn(ExperimentalCoroutinesApi::class)

package com.memento.presentation

import com.memento.domain.model.Collection
import com.memento.domain.model.CollectionId
import com.memento.platform.contract.fakes.FakeLocationProvider
import com.memento.platform.contract.fakes.FakePhotoPickerService
import com.memento.storage.memory.InMemoryCollectionRepository
import com.memento.storage.memory.InMemoryMementoRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RecordCollectionSelectionTest {
    private fun collection(id: String, name: String): Collection = Collection(
        id = CollectionId(id),
        name = name,
        createdAt = Instant.fromEpochMilliseconds(0),
    )

    @Test
    fun availableCollectionsIsPopulatedFromRepository() = runTest {
        val collectionRepository = InMemoryCollectionRepository()
        collectionRepository.saveCollection(collection("c2", "Zeta"))
        collectionRepository.saveCollection(collection("c1", "Alpha"))
        val viewModel = RecordMementoViewModel(
            mementoRepository = InMemoryMementoRepository(),
            locationProvider = FakeLocationProvider(),
            photoPicker = FakePhotoPickerService(),
            collectionRepository = collectionRepository,
            scope = newViewModelScope(),
        )

        advanceUntilIdle()

        assertEquals(
            listOf("Alpha", "Zeta"),
            viewModel.state.value.availableCollections.map { it.name },
        )

        viewModel.dispose()
        advanceUntilIdle()
    }

    @Test
    fun togglingCollectionAddsThenRemovesIt() = runTest {
        val collectionRepository = InMemoryCollectionRepository()
        collectionRepository.saveCollection(collection("c1", "Alpha"))
        val viewModel = RecordMementoViewModel(
            mementoRepository = InMemoryMementoRepository(),
            locationProvider = FakeLocationProvider(),
            photoPicker = FakePhotoPickerService(),
            collectionRepository = collectionRepository,
            scope = newViewModelScope(),
        )

        advanceUntilIdle()
        val id = viewModel.state.value.availableCollections.single().id

        viewModel.onCollectionToggled(id)
        assertTrue(id in viewModel.state.value.collectionIds)

        viewModel.onCollectionToggled(id)
        assertFalse(id in viewModel.state.value.collectionIds)

        viewModel.dispose()
        advanceUntilIdle()
    }

    @Test
    fun savedMementoCarriesSelectedCollectionIds() = runTest {
        val collectionRepository = InMemoryCollectionRepository()
        collectionRepository.saveCollection(collection("c1", "Alpha"))
        val mementoRepository = InMemoryMementoRepository()
        val viewModel = RecordMementoViewModel(
            mementoRepository = mementoRepository,
            locationProvider = FakeLocationProvider(),
            photoPicker = FakePhotoPickerService(),
            collectionRepository = collectionRepository,
            scope = newViewModelScope(),
        )

        advanceUntilIdle()
        val id = viewModel.state.value.availableCollections.single().id
        viewModel.onAddFromCamera()
        viewModel.onTitleChanged("Tokyo Konbini")
        viewModel.onCollectionToggled(id)
        advanceUntilIdle()

        viewModel.onSaveClicked()
        advanceUntilIdle()

        assertNotNull(viewModel.state.value.savedMementoId)
        assertTrue(viewModel.state.value.errors.isEmpty())
        val persisted = mementoRepository.observeAllMementos().first()
        assertEquals(listOf(id), persisted.single().collectionIds)

        viewModel.dispose()
        advanceUntilIdle()
    }

    @Test
    fun nullCollectionRepositoryLeavesAvailableCollectionsEmpty() = runTest {
        val viewModel = RecordMementoViewModel(
            mementoRepository = InMemoryMementoRepository(),
            locationProvider = FakeLocationProvider(),
            photoPicker = FakePhotoPickerService(),
            scope = newViewModelScope(),
        )

        advanceUntilIdle()

        assertTrue(viewModel.state.value.availableCollections.isEmpty())

        viewModel.dispose()
        advanceUntilIdle()
    }

    @Test
    fun resetKeepsAvailableCollections() = runTest {
        val collectionRepository = InMemoryCollectionRepository()
        collectionRepository.saveCollection(collection("c1", "Alpha"))
        val viewModel = RecordMementoViewModel(
            mementoRepository = InMemoryMementoRepository(),
            locationProvider = FakeLocationProvider(),
            photoPicker = FakePhotoPickerService(),
            collectionRepository = collectionRepository,
            scope = newViewModelScope(),
        )

        advanceUntilIdle()
        viewModel.onTitleChanged("Something")
        viewModel.reset()

        assertEquals(1, viewModel.state.value.availableCollections.size)
        assertEquals("", viewModel.state.value.title)

        viewModel.dispose()
        advanceUntilIdle()
    }
}
