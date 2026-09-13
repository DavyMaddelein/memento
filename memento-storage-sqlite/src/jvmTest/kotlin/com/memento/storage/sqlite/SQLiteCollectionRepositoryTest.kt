package com.memento.storage.sqlite

import com.memento.domain.model.Collection
import com.memento.domain.model.CollectionId
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
class SQLiteCollectionRepositoryTest {

    @Test
    fun initialRepositoryIsEmpty() = runTest {
        val repo = SQLiteCollectionRepository(newInMemoryDriver())

        assertEquals(emptyList(), repo.observeCollections().first())
        assertNull(repo.getCollection(CollectionId("missing")))
    }

    @Test
    fun saveThenGetRoundTripsFullAggregate() = runTest {
        val repo = SQLiteCollectionRepository(newInMemoryDriver())
        val collection = fullCollection()

        assertTrue(repo.saveCollection(collection).isSuccess)

        assertEquals(collection, repo.getCollection(collection.id))
    }

    @Test
    fun saveThenObserveEmitsCollectionsSortedByName() = runTest {
        val repo = SQLiteCollectionRepository(newInMemoryDriver())
        repo.saveCollection(fullCollection(id = "c1")).getOrThrow()
        repo.saveCollection(
            fullCollection(id = "c2").copy(
                name = "Alpha Collection",
                categories = emptyList(),
                items = emptyList(),
            ),
        ).getOrThrow()

        assertEquals(
            listOf("Alpha Collection", "Japan Konbini Drinks 2026"),
            repo.observeCollections().first().map { it.name },
        )
    }

    @Test
    fun savingSameIdReplacesChildGraph() = runTest {
        val repo = SQLiteCollectionRepository(newInMemoryDriver())
        repo.saveCollection(fullCollection(id = "c1")).getOrThrow()

        val updated = fullCollection(id = "c1").copy(
            name = "Renamed",
            categories = emptyList(),
            items = emptyList(),
        )
        repo.saveCollection(updated).getOrThrow()

        val loaded = repo.getCollection(CollectionId("c1"))
        assertEquals(updated, loaded)
        assertEquals(1, repo.observeCollections().first().size)
    }

    @Test
    fun deleteCollectionRemovesItAndChildren() = runTest {
        val repo = SQLiteCollectionRepository(newInMemoryDriver())
        repo.saveCollection(fullCollection(id = "c1")).getOrThrow()
        repo.saveCollection(fullCollection(id = "c2")).getOrThrow()

        assertTrue(repo.deleteCollection(CollectionId("c1")).isSuccess)

        assertNull(repo.getCollection(CollectionId("c1")))
        assertEquals(listOf("c2"), repo.observeCollections().first().map { it.id.value })
    }

    @Test
    fun observeCollectionEmitsNullThenValueThenNull() = runTest {
        val repo = SQLiteCollectionRepository(newInMemoryDriver())
        val id = CollectionId("observed")
        val emissions = mutableListOf<Collection?>()
        val job = backgroundScope.launch {
            repo.observeCollection(id).take(3).toList(emissions)
        }

        runCurrent()
        repo.saveCollection(fullCollection(id = "observed")).getOrThrow()
        runCurrent()
        repo.deleteCollection(id).getOrThrow()
        runCurrent()
        job.join()

        assertEquals(3, emissions.size)
        assertNull(emissions[0])
        assertEquals("Japan Konbini Drinks 2026", emissions[1]?.name)
        assertNull(emissions[2])
    }

    @Test
    fun dataSurvivesNewRepositoryInstanceOverSameDriver() = runTest {
        val driver = newInMemoryDriver()
        val collection = fullCollection(id = "persisted")

        SQLiteCollectionRepository(driver).saveCollection(collection).getOrThrow()

        val fresh = SQLiteCollectionRepository(driver)
        assertEquals(collection, fresh.getCollection(CollectionId("persisted")))
    }
}
