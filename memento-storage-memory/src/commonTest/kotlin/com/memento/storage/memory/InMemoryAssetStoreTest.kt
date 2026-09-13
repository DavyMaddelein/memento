package com.memento.storage.memory

import com.memento.domain.model.MediaId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InMemoryAssetStoreTest {

    @Test
    fun saveMediaReturnsReferenceWithNonBlankUri() = runTest {
        val store = InMemoryAssetStore()

        val reference = store.saveMedia(
            bytes = byteArrayOf(1, 2, 3),
            mimeType = "image/png",
            fileName = "photo.png",
        ).getOrThrow()

        assertTrue(reference.uri.isNotBlank())
        assertTrue(reference.mimeType.isNotBlank())
    }

    @Test
    fun readMediaRoundTripsExactBytes() = runTest {
        val store = InMemoryAssetStore()
        val bytes = ByteArray(256) { index -> (index % 256).toByte() }

        val reference = store.saveMedia(bytes, "application/octet-stream", "blob.bin").getOrThrow()

        assertContentEquals(bytes, store.readMedia(reference.id).getOrThrow())
    }

    @Test
    fun saveMediaAssignsDistinctIds() = runTest {
        val store = InMemoryAssetStore()

        val first = store.saveMedia(byteArrayOf(1), "image/png", "a.png").getOrThrow()
        val second = store.saveMedia(byteArrayOf(2), "image/png", "b.png").getOrThrow()

        assertTrue(first.id != second.id)
    }

    @Test
    fun readMediaFailsAfterDelete() = runTest {
        val store = InMemoryAssetStore()
        val reference = store.saveMedia(byteArrayOf(9, 9), "image/jpeg", "x.jpg").getOrThrow()

        assertTrue(store.deleteMedia(reference.id).isSuccess)

        assertTrue(store.readMedia(reference.id).isFailure)
    }

    @Test
    fun deleteMissingMediaDoesNotFail() = runTest {
        val store = InMemoryAssetStore()

        assertTrue(store.deleteMedia(MediaId("missing")).isSuccess)
    }

    @Test
    fun listMediaReflectsSaves() = runTest {
        val store = InMemoryAssetStore()
        val first = store.saveMedia(byteArrayOf(1), "image/png", "a.png").getOrThrow()
        val second = store.saveMedia(byteArrayOf(2), "image/png", "b.png").getOrThrow()

        assertEquals(setOf(first.id, second.id), store.listMedia().toSet())
    }

    @Test
    fun listMediaEmptiesAfterDelete() = runTest {
        val store = InMemoryAssetStore()
        val reference = store.saveMedia(byteArrayOf(1), "image/png", "a.png").getOrThrow()

        store.deleteMedia(reference.id)

        assertEquals(emptyList(), store.listMedia())
    }
}
