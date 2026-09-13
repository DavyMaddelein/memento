package com.memento.storage.memory

import com.memento.domain.model.CollectionId
import com.memento.domain.model.CollectionCategory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class InMemoryCollectionRepositoryTest {

    @Test
    fun initialRepositoryIsEmpty() = runTest {
        val repo = InMemoryCollectionRepository()

        assertEquals(emptyList(), repo.observeCollections().first())
        assertNull(repo.getCollection(CollectionId("missing")))
    }

    @Test
    fun saveThenGetReturnsTheCollection() = runTest {
        val repo = InMemoryCollectionRepository()
        val collection = memoryCollection(
            id = "c1",
            name = "Japan Konbini Drinks",
            categories = listOf(CollectionCategory("cat1", "Coffee")),
        )

        assertTrue(repo.saveCollection(collection).isSuccess)

        assertEquals(collection, repo.getCollection(CollectionId("c1")))
    }

    @Test
    fun observeCollectionsEmitsSavedCollections() = runTest {
        val repo = InMemoryCollectionRepository()

        repo.saveCollection(memoryCollection(id = "c1", name = "Alpha"))
        repo.saveCollection(memoryCollection(id = "c2", name = "Beta"))

        val names = repo.observeCollections().first().map { it.name }
        assertEquals(listOf("Alpha", "Beta"), names)
    }

    @Test
    fun saveCollectionWithSameIdReplaces() = runTest {
        val repo = InMemoryCollectionRepository()

        repo.saveCollection(memoryCollection(id = "c1", name = "Before"))
        repo.saveCollection(memoryCollection(id = "c1", name = "After"))

        val all = repo.observeCollections().first()
        assertEquals(1, all.size)
        assertEquals("After", all.single().name)
    }

    @Test
    fun deleteCollectionRemovesIt() = runTest {
        val repo = InMemoryCollectionRepository()
        repo.saveCollection(memoryCollection(id = "c1"))
        repo.saveCollection(memoryCollection(id = "c2"))

        assertTrue(repo.deleteCollection(CollectionId("c1")).isSuccess)

        assertEquals(listOf("c2"), repo.observeCollections().first().map { it.id.value })
        assertNull(repo.getCollection(CollectionId("c1")))
    }

    @Test
    fun observeCollectionEmitsNullThenValueThenNull() = runTest {
        val repo = InMemoryCollectionRepository()
        val id = CollectionId("c-observed")
        val emissions = mutableListOf<com.memento.domain.model.Collection?>()
        val job = backgroundScope.launch {
            repo.observeCollection(id).take(3).toList(emissions)
        }

        runCurrent()
        repo.saveCollection(memoryCollection(id = "c-observed", name = "Observed"))
        runCurrent()
        repo.deleteCollection(id)
        runCurrent()
        job.join()

        assertEquals(3, emissions.size)
        assertNull(emissions[0])
        assertEquals("Observed", emissions[1]?.name)
        assertNull(emissions[2])
    }
}
