package com.memento.storage.memory

import com.memento.domain.model.CollectionId
import com.memento.domain.model.Coordinates
import com.memento.domain.model.MementoId
import com.memento.domain.model.Place
import com.memento.domain.model.Rating
import com.memento.domain.model.Tag
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
class InMemoryMementoRepositoryTest {

    @Test
    fun initialRepositoryIsEmpty() = runTest {
        val repo = InMemoryMementoRepository()

        assertEquals(emptyList(), repo.observeAllMementos().first())
        assertNull(repo.getMemento(MementoId("missing")))
    }

    @Test
    fun saveThenObserveAllEmitsTheSavedMemento() = runTest {
        val repo = InMemoryMementoRepository()

        val result = repo.saveMemento(memoryMemento(id = "m1", title = "First"))

        assertTrue(result.isSuccess)
        val all = repo.observeAllMementos().first()
        assertEquals(listOf("m1"), all.map { it.id.value })
        assertEquals("First", all.single().title)
    }

    @Test
    fun getMementoReturnsSavedAggregate() = runTest {
        val repo = InMemoryMementoRepository()
        val original = memoryMemento(
            id = "m1",
            title = "Roppongi",
            coordinates = Coordinates(35.66, 139.73, 12.0),
            place = Place(name = "Konbini", city = "Tokyo", country = "Japan"),
            rating = Rating(5),
            tags = listOf(Tag("coffee"), Tag("travel")),
            collectionIds = listOf(CollectionId("c1")),
        )

        repo.saveMemento(original)

        assertEquals(original, repo.getMemento(MementoId("m1")))
    }

    @Test
    fun savingSameIdReplacesExistingMemento() = runTest {
        val repo = InMemoryMementoRepository()

        repo.saveMemento(memoryMemento(id = "m1", title = "Before"))
        repo.saveMemento(memoryMemento(id = "m1", title = "After"))

        val all = repo.observeAllMementos().first()
        assertEquals(1, all.size)
        assertEquals("After", all.single().title)
    }

    @Test
    fun deleteMementoRemovesIt() = runTest {
        val repo = InMemoryMementoRepository()
        repo.saveMemento(memoryMemento(id = "m1"))
        repo.saveMemento(memoryMemento(id = "m2"))

        val result = repo.deleteMemento(MementoId("m1"))

        assertTrue(result.isSuccess)
        assertEquals(listOf("m2"), repo.observeAllMementos().first().map { it.id.value })
        assertNull(repo.getMemento(MementoId("m1")))
    }

    @Test
    fun saveMementosPersistsEveryEntry() = runTest {
        val repo = InMemoryMementoRepository()

        val result = repo.saveMementos(
            listOf(
                memoryMemento(id = "m1", occurredAt = T1),
                memoryMemento(id = "m2", occurredAt = T2),
                memoryMemento(id = "m3", occurredAt = T3),
            ),
        )

        assertTrue(result.isSuccess)
        assertEquals(3, repo.observeAllMementos().first().size)
    }

    @Test
    fun deleteAllMementosClearsTheStore() = runTest {
        val repo = InMemoryMementoRepository()
        repo.saveMementos(listOf(memoryMemento(id = "m1"), memoryMemento(id = "m2")))

        val result = repo.deleteAllMementos()

        assertTrue(result.isSuccess)
        assertEquals(emptyList(), repo.observeAllMementos().first())
    }

    @Test
    fun observeAllOrdersNewestOccurredAtFirst() = runTest {
        val repo = InMemoryMementoRepository()
        repo.saveMementos(
            listOf(
                memoryMemento(id = "oldest", occurredAt = T1),
                memoryMemento(id = "newest", occurredAt = T3),
                memoryMemento(id = "middle", occurredAt = T2),
            ),
        )

        val ordered = repo.observeAllMementos().first().map { it.id.value }

        assertEquals(listOf("newest", "middle", "oldest"), ordered)
    }

    @Test
    fun observeMementoEmitsNullThenValueThenNull() = runTest {
        val repo = InMemoryMementoRepository()
        val id = MementoId("m-observed")
        val emissions = mutableListOf<com.memento.domain.model.Memento?>()
        val job = backgroundScope.launch {
            repo.observeMemento(id).take(3).toList(emissions)
        }

        runCurrent()
        repo.saveMemento(memoryMemento(id = "m-observed", title = "Observed"))
        runCurrent()
        repo.deleteMemento(id)
        runCurrent()
        job.join()

        assertEquals(3, emissions.size)
        assertNull(emissions[0])
        assertEquals("Observed", emissions[1]?.title)
        assertNull(emissions[2])
    }

    @Test
    fun concurrentSavesAllPersist() = runTest {
        val repo = InMemoryMementoRepository()

        val results = (1..100).map { index ->
            async { repo.saveMemento(memoryMemento(id = "m-$index", title = "Title $index")) }
        }.awaitAll()

        assertTrue(results.all { it.isSuccess })
        val persisted = repo.observeAllMementos().first()
        assertEquals(100, persisted.size)
        assertEquals((1..100).map { "m-$it" }.toSet(), persisted.map { it.id.value }.toSet())
    }
}
