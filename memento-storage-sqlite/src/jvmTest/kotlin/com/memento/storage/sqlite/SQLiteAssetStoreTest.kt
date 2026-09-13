package com.memento.storage.sqlite

import com.memento.domain.model.MediaId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SQLiteAssetStoreTest {

    @Test
    fun saveMediaReturnsReferenceWithNonBlankUri() = runTest {
        val store = SQLiteAssetStore(newInMemoryDriver())

        val reference = store.saveMedia(byteArrayOf(1, 2, 3), "image/png", "photo.png").getOrThrow()

        assertTrue(reference.uri.isNotBlank())
        assertTrue(reference.mimeType.isNotBlank())
    }

    @Test
    fun readMediaRoundTripsExactBytes() = runTest {
        val store = SQLiteAssetStore(newInMemoryDriver())
        val bytes = ByteArray(2048) { index -> (index % 256).toByte() }

        val reference = store.saveMedia(bytes, "application/octet-stream", "blob.bin").getOrThrow()

        assertContentEquals(bytes, store.readMedia(reference.id).getOrThrow())
    }

    @Test
    fun readMediaFailsAfterDelete() = runTest {
        val store = SQLiteAssetStore(newInMemoryDriver())
        val reference = store.saveMedia(byteArrayOf(9, 9), "image/jpeg", "x.jpg").getOrThrow()

        assertTrue(store.deleteMedia(reference.id).isSuccess)

        assertTrue(store.readMedia(reference.id).isFailure)
    }

    @Test
    fun listMediaReflectsSaves() = runTest {
        val store = SQLiteAssetStore(newInMemoryDriver())
        val first = store.saveMedia(byteArrayOf(1), "image/png", "a.png").getOrThrow()
        val second = store.saveMedia(byteArrayOf(2), "image/png", "b.png").getOrThrow()

        assertEquals(setOf(first.id, second.id), store.listMedia().toSet())
    }

    @Test
    fun deleteMissingMediaSucceeds() = runTest {
        val store = SQLiteAssetStore(newInMemoryDriver())

        assertTrue(store.deleteMedia(MediaId("missing")).isSuccess)
    }

    @Test
    fun dataSurvivesNewAssetStoreInstanceOverSameDriver() = runTest {
        val driver = newInMemoryDriver()
        val bytes = byteArrayOf(4, 5, 6, 7)
        val reference = SQLiteAssetStore(driver).saveMedia(bytes, "image/png", "keep.png").getOrThrow()

        val fresh = SQLiteAssetStore(driver)
        assertContentEquals(bytes, fresh.readMedia(reference.id).getOrThrow())
        assertEquals(listOf(reference.id), fresh.listMedia())
    }
}
