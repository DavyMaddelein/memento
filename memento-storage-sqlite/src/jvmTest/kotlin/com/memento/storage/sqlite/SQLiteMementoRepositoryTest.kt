package com.memento.storage.sqlite

import com.memento.domain.model.Collection
import com.memento.domain.model.Memento
import com.memento.domain.model.MementoId
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
class SQLiteMementoRepositoryTest {

    @Test
    fun initialRepositoryIsEmpty() = runTest {
        val repo = SQLiteMementoRepository(newInMemoryDriver())

        assertEquals(emptyList(), repo.observeAllMementos().first())
        assertNull(repo.getMemento(MementoId("missing")))
    }

    @Test
    fun saveThenGetRoundTripsFullAggregate() = runTest {
        val driver = newInMemoryDriver()
        val repo = SQLiteMementoRepository(driver)
        val memento = fullMemento()

        assertTrue(repo.saveMemento(memento).isSuccess)

        assertEquals(memento, repo.getMemento(memento.id))
    }

    @Test
    fun saveThenObserveAllEmitsTheMemento() = runTest {
        val repo = SQLiteMementoRepository(newInMemoryDriver())
        val memento = fullMemento(id = "m1")

        repo.saveMemento(memento).getOrThrow()

        assertEquals(listOf(memento), repo.observeAllMementos().first())
    }

    @Test
    fun savingSameIdReplacesAggregateIncludingChildren() = runTest {
        val repo = SQLiteMementoRepository(newInMemoryDriver())
        repo.saveMemento(fullMemento(id = "m1")).getOrThrow()

        val updated = minimalMemento(id = "m1", occurredAt = T3).copy(title = "Updated")
        repo.saveMemento(updated).getOrThrow()

        val loaded = repo.getMemento(MementoId("m1"))
        assertEquals(updated, loaded)
        assertEquals(1, repo.observeAllMementos().first().size)
    }

    @Test
    fun deleteMementoRemovesItAndChildren() = runTest {
        val repo = SQLiteMementoRepository(newInMemoryDriver())
        repo.saveMemento(fullMemento(id = "m1")).getOrThrow()
        repo.saveMemento(fullMemento(id = "m2")).getOrThrow()

        assertTrue(repo.deleteMemento(MementoId("m1")).isSuccess)

        assertNull(repo.getMemento(MementoId("m1")))
        assertEquals(listOf("m2"), repo.observeAllMementos().first().map { it.id.value })
    }

    @Test
    fun deleteAllMementosClearsStore() = runTest {
        val repo = SQLiteMementoRepository(newInMemoryDriver())
        repo.saveMementos(listOf(fullMemento(id = "m1"), fullMemento(id = "m2"))).getOrThrow()

        assertTrue(repo.deleteAllMementos().isSuccess)

        assertEquals(emptyList(), repo.observeAllMementos().first())
    }

    @Test
    fun saveMementosPersistsEveryEntry() = runTest {
        val repo = SQLiteMementoRepository(newInMemoryDriver())

        assertTrue(
            repo.saveMementos(
                listOf(
                    minimalMemento(id = "m1", occurredAt = T1),
                    minimalMemento(id = "m2", occurredAt = T2),
                    minimalMemento(id = "m3", occurredAt = T3),
                ),
            ).isSuccess,
        )

        assertEquals(3, repo.observeAllMementos().first().size)
    }

    @Test
    fun observeAllOrdersNewestOccurredAtFirst() = runTest {
        val repo = SQLiteMementoRepository(newInMemoryDriver())
        repo.saveMementos(
            listOf(
                minimalMemento(id = "oldest", occurredAt = T1),
                minimalMemento(id = "newest", occurredAt = T3),
                minimalMemento(id = "middle", occurredAt = T2),
            ),
        ).getOrThrow()

        assertEquals(
            listOf("newest", "middle", "oldest"),
            repo.observeAllMementos().first().map { it.id.value },
        )
    }

    @Test
    fun observeAllEmitsAfterEachMutation() = runTest {
        val repo = SQLiteMementoRepository(newInMemoryDriver())
        val emissions = mutableListOf<List<Memento>>()
        val job = backgroundScope.launch {
            repo.observeAllMementos().take(3).toList(emissions)
        }

        runCurrent()
        repo.saveMemento(minimalMemento(id = "a", occurredAt = T1)).getOrThrow()
        runCurrent()
        repo.saveMemento(minimalMemento(id = "b", occurredAt = T2)).getOrThrow()
        runCurrent()
        job.join()

        assertEquals(0, emissions[0].size)
        assertEquals(1, emissions[1].size)
        assertEquals(2, emissions[2].size)
    }

    @Test
    fun observeMementoEmitsNullThenValueThenNull() = runTest {
        val repo = SQLiteMementoRepository(newInMemoryDriver())
        val id = MementoId("observed")
        val emissions = mutableListOf<Memento?>()
        val job = backgroundScope.launch {
            repo.observeMemento(id).take(3).toList(emissions)
        }

        runCurrent()
        repo.saveMemento(minimalMemento(id = "observed", occurredAt = T1)).getOrThrow()
        runCurrent()
        repo.deleteMemento(id).getOrThrow()
        runCurrent()
        job.join()

        assertEquals(3, emissions.size)
        assertNull(emissions[0])
        assertEquals("Minimal", emissions[1]?.title)
        assertNull(emissions[2])
    }

    @Test
    fun dataSurvivesNewRepositoryInstanceOverSameDriver() = runTest {
        val driver = newInMemoryDriver()
        val memento = fullMemento(id = "persisted")

        SQLiteMementoRepository(driver).saveMemento(memento).getOrThrow()

        val fresh = SQLiteMementoRepository(driver)
        assertEquals(memento, fresh.getMemento(MementoId("persisted")))
        assertEquals(listOf(memento), fresh.observeAllMementos().first())
    }
}
