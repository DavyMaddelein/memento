@file:OptIn(ExperimentalCoroutinesApi::class)

package com.memento.presentation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.memento.domain.collection.KonbiniDrinkChecklist
import com.memento.storage.memory.InMemoryCollectionRepository
import com.memento.storage.memory.InMemoryMementoRepository
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CollectionDetailViewModelTest {
    @Test
    fun progressRecalculatesWhenMatchingMementoIsAdded() = runTest {
        val now = Instant.fromEpochMilliseconds(0)
        val collection = KonbiniDrinkChecklist.create(now)
        val collectionRepository = InMemoryCollectionRepository()
        collectionRepository.saveCollection(collection)
        val mementoRepository = InMemoryMementoRepository()
        val viewModel = CollectionDetailViewModel(
            collectionRepository = collectionRepository,
            mementoRepository = mementoRepository,
            scope = newViewModelScope(),
        )

        viewModel.selectCollection(collection.id)
        advanceUntilIdle()

        val initial = viewModel.state.value
        assertFalse(initial.isLoading)
        assertEquals(collection, initial.collection)
        val initialProgress = assertNotNull(initial.progress)
        assertEquals(collection.items.size, initialProgress.totalItems)
        assertEquals(0, initialProgress.completedItems)
        assertEquals(0, initialProgress.percentage)
        assertEquals(collection.categories.size, initialProgress.categories.size)
        assertEquals(
            0,
            initialProgress.categories.first {
                it.categoryId == KonbiniDrinkChecklist.CATEGORY_CANNED_COFFEE
            }.completed,
        )

        mementoRepository.saveMemento(
            testMemento(id = "boss-rainbow", tags = listOf("boss-rainbow")),
        )
        advanceUntilIdle()

        val updated = viewModel.state.value.progress
        assertNotNull(updated)
        assertEquals(1, updated.completedItems)
        assertEquals((1 * 100) / collection.items.size, updated.percentage)
        assertEquals(setOf("boss-rainbow"), updated.completedItemIds)

        val canned = updated.categories.first {
            it.categoryId == KonbiniDrinkChecklist.CATEGORY_CANNED_COFFEE
        }
        assertEquals(collection.items.count { it.categoryId == KonbiniDrinkChecklist.CATEGORY_CANNED_COFFEE }, canned.total)
        assertEquals(1, canned.completed)

        val greenTea = updated.categories.first {
            it.categoryId == KonbiniDrinkChecklist.CATEGORY_GREEN_TEA
        }
        assertEquals(collection.items.count { it.categoryId == KonbiniDrinkChecklist.CATEGORY_GREEN_TEA }, greenTea.total)
        assertEquals(0, greenTea.completed)

        viewModel.dispose()
        advanceUntilIdle()
    }

    @Test
    fun autoSelectsSingleCollectionWithoutExplicitSelection() = runTest {
        val collection = KonbiniDrinkChecklist.create(Instant.fromEpochMilliseconds(0))
        val collectionRepository = InMemoryCollectionRepository()
        collectionRepository.saveCollection(collection)
        val mementoRepository = InMemoryMementoRepository()
        val viewModel = CollectionDetailViewModel(
            collectionRepository = collectionRepository,
            mementoRepository = mementoRepository,
            scope = newViewModelScope(),
        )

        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals(collection.id, state.collection?.id)
        assertNotNull(state.progress)

        viewModel.dispose()
        advanceUntilIdle()
    }

    @Test
    fun initialLoadDoesNotPopulateRecentlyEarnedAndExposesBoard() = runTest {
        val collection = KonbiniDrinkChecklist.create(Instant.fromEpochMilliseconds(0))
        val collectionRepository = InMemoryCollectionRepository()
        collectionRepository.saveCollection(collection)
        val mementoRepository = InMemoryMementoRepository()
        val viewModel = CollectionDetailViewModel(
            collectionRepository = collectionRepository,
            mementoRepository = mementoRepository,
            scope = newViewModelScope(),
        )

        viewModel.selectCollection(collection.id)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.recentlyEarned.isEmpty())
        val board = assertNotNull(state.board)
        assertEquals(collection.items.size + collection.categories.size + 1, board.totalCount)
        assertEquals(0, board.earnedCount)
        assertNotNull(board.meta)

        viewModel.dispose()
        advanceUntilIdle()
    }

    @Test
    fun toggleHideCompletedUpdatesState() = runTest {
        val collection = KonbiniDrinkChecklist.create(Instant.fromEpochMilliseconds(0))
        val collectionRepository = InMemoryCollectionRepository()
        collectionRepository.saveCollection(collection)
        val mementoRepository = InMemoryMementoRepository()
        val viewModel = CollectionDetailViewModel(
            collectionRepository = collectionRepository,
            mementoRepository = mementoRepository,
            scope = newViewModelScope(),
        )

        viewModel.selectCollection(collection.id)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.hideCompleted)
        viewModel.onToggleHideCompleted()
        assertTrue(viewModel.state.value.hideCompleted)
        viewModel.onToggleHideCompleted()
        assertFalse(viewModel.state.value.hideCompleted)

        viewModel.dispose()
        advanceUntilIdle()
    }

    @Test
    fun addingCompletingMementoPopulatesRecentlyEarnedUntilAcknowledged() = runTest {
        val collection = KonbiniDrinkChecklist.create(Instant.fromEpochMilliseconds(0))
        val collectionRepository = InMemoryCollectionRepository()
        collectionRepository.saveCollection(collection)
        val mementoRepository = InMemoryMementoRepository()
        val viewModel = CollectionDetailViewModel(
            collectionRepository = collectionRepository,
            mementoRepository = mementoRepository,
            scope = newViewModelScope(),
        )

        viewModel.selectCollection(collection.id)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.recentlyEarned.isEmpty())

        mementoRepository.saveMemento(
            testMemento(id = "boss-rainbow", tags = listOf("boss-rainbow")),
        )
        advanceUntilIdle()

        val earned = viewModel.state.value.recentlyEarned
        assertEquals(listOf("item:boss-rainbow"), earned.map { it.id })
        assertTrue(earned.single().earned)

        viewModel.acknowledgeEarned()
        assertTrue(viewModel.state.value.recentlyEarned.isEmpty())

        mementoRepository.saveMemento(
            testMemento(id = "unrelated", title = "Something unrelated"),
        )
        advanceUntilIdle()
        assertTrue(viewModel.state.value.recentlyEarned.isEmpty())

        viewModel.dispose()
        advanceUntilIdle()
    }
}
