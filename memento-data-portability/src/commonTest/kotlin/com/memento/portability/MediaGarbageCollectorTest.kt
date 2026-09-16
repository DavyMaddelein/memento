package com.memento.portability

import com.memento.domain.model.Memento
import com.memento.domain.model.MementoId
import com.memento.storage.memory.InMemoryAssetStore
import com.memento.storage.memory.InMemoryMementoRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MediaGarbageCollectorTest {

    private val t = Instant.parse("2024-01-01T00:00:00Z")

    private fun memento(id: String, media: List<com.memento.domain.model.MediaReference>) = Memento(
        id = MementoId(id),
        title = "Title",
        media = media,
        occurredAt = t,
        createdAt = t,
        updatedAt = t,
    )

    @Test
    fun deletesOnlyUnreferencedMedia() = runTest {
        val store = InMemoryAssetStore()
        val referenced = store.saveMedia(byteArrayOf(1), "image/jpeg", "keep.jpg").getOrThrow()
        val orphan = store.saveMedia(byteArrayOf(2), "image/jpeg", "orphan.jpg").getOrThrow()
        val repository = InMemoryMementoRepository()
        repository.saveMemento(memento("m1", listOf(referenced)))

        val removed = MediaGarbageCollector(repository, store).collect()

        assertEquals(1, removed)
        assertTrue(store.readMedia(referenced.id).isSuccess)
        assertTrue(store.readMedia(orphan.id).isFailure)
    }

    @Test
    fun keepsSharedMediaReferencedByMultipleMementos() = runTest {
        val store = InMemoryAssetStore()
        val shared = store.saveMedia(byteArrayOf(9), "image/jpeg", "shared.jpg").getOrThrow()
        val repository = InMemoryMementoRepository()
        repository.saveMementos(listOf(memento("m1", listOf(shared)), memento("m2", listOf(shared))))

        assertEquals(0, MediaGarbageCollector(repository, store).collect())
        assertTrue(store.readMedia(shared.id).isSuccess)
    }

    @Test
    fun removesAllMediaWhenNoMementosExist() = runTest {
        val store = InMemoryAssetStore()
        store.saveMedia(byteArrayOf(1), "image/jpeg", "a.jpg").getOrThrow()
        store.saveMedia(byteArrayOf(2), "image/jpeg", "b.jpg").getOrThrow()

        assertEquals(2, MediaGarbageCollector(InMemoryMementoRepository(), store).collect())
    }
}
