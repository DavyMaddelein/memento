package com.memento.portability

import com.memento.domain.model.CollectionId
import com.memento.domain.model.Coordinates
import com.memento.domain.model.Memento
import com.memento.domain.model.MementoId
import com.memento.domain.model.Place
import com.memento.domain.model.Rating
import com.memento.domain.model.Tag
import com.memento.domain.model.TastingNotes
import com.memento.storage.memory.InMemoryAssetStore
import com.memento.storage.memory.InMemoryMementoRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ZipImportEngineTest {

    @Test
    fun fullRoundtripPreservesDataAndMediaBytes() = runTest {
        val sourceRepo = InMemoryMementoRepository()
        val sourceStore = InMemoryAssetStore()

        val bytesA = byteArrayOf(1, 2, 3, 4, 5)
        val bytesB = "png-payload".encodeToByteArray()
        val refA = sourceStore.saveMedia(bytesA, "image/jpeg", "a.jpg").getOrThrow()
        val refB = sourceStore.saveMedia(bytesB, "image/png", "b.png").getOrThrow()

        val m1 = Memento(
            id = MementoId("m-1"),
            title = "Ramen",
            reflection = "Warm and cosy",
            coordinates = Coordinates(35.0, 139.0, 5.0),
            place = Place("Ichiran", "Ichiran", "Shinjuku", "Tokyo", "Japan"),
            media = listOf(refA),
            rating = Rating(4),
            tastingNotes = TastingNotes("Rich tonkotsu broth", listOf("umami", "spicy")),
            tags = listOf(Tag("food"), Tag("tokyo")),
            collectionIds = listOf(CollectionId("c-1")),
            priceMinorUnits = 1200L,
            currencyCode = "JPY",
            occurredAt = Instant.parse("2024-03-01T10:00:00Z"),
            createdAt = Instant.parse("2024-03-01T11:00:00Z"),
            updatedAt = Instant.parse("2024-03-01T12:00:00Z"),
        )
        val m2 = Memento(
            id = MementoId("m-2"),
            title = "Sunset",
            media = listOf(refA, refB),
            occurredAt = Instant.parse("2024-02-01T10:00:00Z"),
            createdAt = Instant.parse("2024-02-01T10:00:00Z"),
            updatedAt = Instant.parse("2024-02-01T10:00:00Z"),
        )
        val m3 = simpleMemento("m-3", "2024-01-01T10:00:00Z", title = "Empty")
        sourceRepo.saveMementos(listOf(m1, m2, m3)).getOrThrow()

        val archive = ZipExportEngine(sourceStore).exportAll(sourceRepo).getOrThrow()

        val targetRepo = InMemoryMementoRepository()
        val targetStore = InMemoryAssetStore()
        val report = ZipImportEngine(targetRepo, targetStore).import(archive).getOrThrow()

        assertEquals(3, report.imported)
        assertEquals(0, report.skipped)
        assertEquals(3, report.mediaRestored)
        assertTrue(report.errors.isEmpty(), "unexpected errors: ${report.errors}")

        val imported = targetRepo.observeAllMementos().first().associateBy { it.id.value }
        assertEquals(setOf("m-1", "m-2", "m-3"), imported.keys)

        val restored1 = imported.getValue("m-1")
        assertEquals("Ramen", restored1.title)
        assertEquals("Warm and cosy", restored1.reflection)
        assertEquals(Coordinates(35.0, 139.0, 5.0), restored1.coordinates)
        assertEquals(Place("Ichiran", "Ichiran", "Shinjuku", "Tokyo", "Japan"), restored1.place)
        assertEquals(Rating(4), restored1.rating)
        assertEquals(TastingNotes("Rich tonkotsu broth", listOf("umami", "spicy")), restored1.tastingNotes)
        assertEquals(listOf(Tag("food"), Tag("tokyo")), restored1.tags)
        assertEquals(listOf(CollectionId("c-1")), restored1.collectionIds)
        assertEquals(1200L, restored1.priceMinorUnits)
        assertEquals("JPY", restored1.currencyCode)
        assertEquals(m1.occurredAt, restored1.occurredAt)
        assertEquals(m1.createdAt, restored1.createdAt)
        assertEquals(m1.updatedAt, restored1.updatedAt)
        assertEquals(refA.capturedAt, restored1.media.single().capturedAt)
        assertEquals(refA.mimeType, restored1.media.single().mimeType)
        assertContentEquals(bytesA, targetStore.readMedia(restored1.media.single().id).getOrThrow())

        val restored2 = imported.getValue("m-2")
        assertEquals(2, restored2.media.size)
        assertContentEquals(bytesA, targetStore.readMedia(restored2.media[0].id).getOrThrow())
        assertContentEquals(bytesB, targetStore.readMedia(restored2.media[1].id).getOrThrow())
    }

    @Test
    fun rejectsNewerSchemaVersion() = runTest {
        val archive = archiveWithSchemaVersion(2)

        val result = ZipImportEngine(InMemoryMementoRepository(), InMemoryAssetStore())
            .import(archive)

        assertTrue(result.isFailure, "expected failure for schema_version 2")
        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(message.contains("schema_version"), "unexpected message: $message")
    }

    @Test
    fun skipExistingKeepsRepositoryStable() = runTest {
        val archive = buildArchive()
        val repo = InMemoryMementoRepository()
        val engine = ZipImportEngine(repo, InMemoryAssetStore())

        val first = engine.import(archive, ConflictPolicy.SkipExisting).getOrThrow()
        assertEquals(2, first.imported)
        assertEquals(0, first.skipped)

        val second = engine.import(archive, ConflictPolicy.SkipExisting).getOrThrow()
        assertEquals(0, second.imported)
        assertEquals(2, second.skipped)
        assertEquals(2, repo.observeAllMementos().first().size)
    }

    @Test
    fun overwriteDoesNotDuplicate() = runTest {
        val archive = buildArchive()
        val repo = InMemoryMementoRepository()
        val engine = ZipImportEngine(repo, InMemoryAssetStore())

        engine.import(archive, ConflictPolicy.Overwrite).getOrThrow()
        val second = engine.import(archive, ConflictPolicy.Overwrite).getOrThrow()

        assertEquals(2, second.imported)
        assertEquals(0, second.skipped)
        assertEquals(2, repo.observeAllMementos().first().size)
    }

    @Test
    fun keepBothWithNewIdDoublesRepositoryAndChangesIds() = runTest {
        val archive = buildArchive()
        val repo = InMemoryMementoRepository()
        val engine = ZipImportEngine(repo, InMemoryAssetStore())

        engine.import(archive, ConflictPolicy.KeepBothWithNewId).getOrThrow()
        val firstIds = repo.observeAllMementos().first().map { it.id.value }.toSet()

        engine.import(archive, ConflictPolicy.KeepBothWithNewId).getOrThrow()
        val allIds = repo.observeAllMementos().first().map { it.id.value }

        assertEquals(4, allIds.size)
        assertEquals(4, allIds.toSet().size)
        val newlyAssigned = allIds.filter { it !in firstIds }
        assertEquals(2, newlyAssigned.size, "conflicting import should assign fresh ids")
        assertNotEquals(emptySet<String>(), firstIds)
    }

    private suspend fun buildArchive(): ByteArray {
        val store = InMemoryAssetStore()
        val repo = InMemoryMementoRepository()
        val bytes = byteArrayOf(7, 7, 7)
        val ref = store.saveMedia(bytes, "image/jpeg", "shared.jpg").getOrThrow()
        val m1 = simpleMemento("b-1", "2024-01-01T00:00:00Z").copy(media = listOf(ref))
        val m2 = simpleMemento("b-2", "2024-02-01T00:00:00Z")
        repo.saveMementos(listOf(m1, m2)).getOrThrow()
        return ZipExportEngine(store).exportAll(repo).getOrThrow()
    }

    private fun archiveWithSchemaVersion(version: Int): ByteArray {
        val backup = MementoBackupV1(
            schemaVersion = version,
            exportedAt = "2024-01-01T00:00:00Z",
            mementos = emptyList(),
        )
        val json = MementoBackupJson.encodeToString(MementoBackupV1.serializer(), backup)
        return ZipWriter.write(listOf(ZipExportEngine.MEMENTOS_JSON to json.encodeToByteArray()))
    }
}
