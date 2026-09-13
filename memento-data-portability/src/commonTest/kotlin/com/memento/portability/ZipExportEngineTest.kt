package com.memento.portability

import com.memento.domain.model.Memento
import com.memento.domain.model.MementoId
import com.memento.storage.memory.InMemoryAssetStore
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ZipExportEngineTest {

    @Test
    fun exportContainsJsonMarkdownAndReferencedMedia() = runTest {
        val assetStore = InMemoryAssetStore()
        val imageBytes = byteArrayOf(10, 20, 30, 40, 50)
        val reference = assetStore.saveMedia(imageBytes, "image/jpeg", "photo.jpg").getOrThrow()
        val memento = Memento(
            id = MementoId("m-1"),
            title = "Coffee",
            media = listOf(reference),
            occurredAt = Instant.parse("2024-01-01T00:00:00Z"),
            createdAt = Instant.parse("2024-01-01T00:00:00Z"),
            updatedAt = Instant.parse("2024-01-01T00:00:00Z"),
        )

        val archive = ZipExportEngine(assetStore).export(listOf(memento)).getOrThrow()
        val entries = ZipReader.read(archive)

        assertTrue(entries.containsKey(ZipExportEngine.MEMENTOS_JSON), "missing mementos.json")
        assertTrue(entries.containsKey(ZipExportEngine.MEMENTOS_MD), "missing mementos.md")
        val mediaPath = "media/${reference.id.value}"
        assertTrue(entries.containsKey(mediaPath), "missing $mediaPath")
        assertContentEquals(imageBytes, entries.getValue(mediaPath))

        val backup = MementoBackupJson.decodeFromString(
            MementoBackupV1.serializer(),
            entries.getValue(ZipExportEngine.MEMENTOS_JSON).decodeToString(),
        )
        assertEquals(1, backup.schemaVersion)
        assertEquals(1, backup.mementos.size)
        assertEquals("m-1", backup.mementos.single().id)
        assertEquals(reference.id.value, backup.mementos.single().media.single().mediaRefId)
    }

    @Test
    fun exportSkipsMissingMediaGracefully() = runTest {
        val assetStore = InMemoryAssetStore()
        val missing = com.memento.domain.model.MediaReference(
            id = com.memento.domain.model.MediaId("media-absent"),
            uri = "memory://absent",
            mimeType = "image/jpeg",
            capturedAt = Instant.parse("2024-01-01T00:00:00Z"),
        )
        val memento = Memento(
            id = MementoId("m-1"),
            title = "No bytes",
            media = listOf(missing),
            occurredAt = Instant.parse("2024-01-01T00:00:00Z"),
            createdAt = Instant.parse("2024-01-01T00:00:00Z"),
            updatedAt = Instant.parse("2024-01-01T00:00:00Z"),
        )

        val engine = ZipExportEngine(assetStore)
        val archive = engine.export(listOf(memento)).getOrThrow()
        val entries = ZipReader.read(archive)

        assertTrue(entries.containsKey(ZipExportEngine.MEMENTOS_JSON))
        assertTrue(entries.none { it.key == "media/media-absent" })
        assertEquals(1, engine.lastWarnings.size)
    }
}
