@file:OptIn(ExperimentalCoroutinesApi::class)

package com.memento.presentation

import com.memento.domain.collection.KonbiniDrinkChecklist
import com.memento.storage.memory.InMemoryCollectionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KonbiniSeederTest {

    private val now = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun seedsTheCollectionWhenAbsent() = runTest {
        val repository = InMemoryCollectionRepository()

        assertTrue(seedKonbiniCollection(repository, now))

        val stored = repository.getCollection(KonbiniDrinkChecklist.COLLECTION_ID)!!
        assertTrue(stored.items.all { !it.japaneseLabel.isNullOrBlank() })
    }

    @Test
    fun repairsLegacyDataMissingJapaneseLabelsAndPreservesCreatedAt() = runTest {
        val repository = InMemoryCollectionRepository()
        val legacyCreatedAt = Instant.parse("2020-01-01T00:00:00Z")
        val legacy = KonbiniDrinkChecklist.create(now).let { canonical ->
            canonical.copy(
                createdAt = legacyCreatedAt,
                items = canonical.items.map { it.copy(japaneseLabel = null) },
            )
        }
        repository.saveCollection(legacy)

        assertTrue(seedKonbiniCollection(repository, now))

        val stored = repository.getCollection(KonbiniDrinkChecklist.COLLECTION_ID)!!
        assertTrue(stored.items.all { !it.japaneseLabel.isNullOrBlank() })
        assertEquals(legacyCreatedAt, stored.createdAt)
    }

    @Test
    fun leavesAnUpToDateCollectionUntouched() = runTest {
        val repository = InMemoryCollectionRepository()
        val current = KonbiniDrinkChecklist.create(now)
        repository.saveCollection(current)

        assertFalse(seedKonbiniCollection(repository, now))

        assertEquals(current, repository.getCollection(KonbiniDrinkChecklist.COLLECTION_ID))
    }
}
