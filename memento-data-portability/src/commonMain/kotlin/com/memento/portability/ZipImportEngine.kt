package com.memento.portability

import com.memento.domain.model.MediaReference
import com.memento.domain.model.MementoId
import com.memento.domain.util.randomId
import com.memento.storage.contract.AssetStore
import com.memento.storage.contract.MementoRepository
import kotlinx.serialization.SerializationException

/** How an import should treat mementos whose id already exists in the target repository. */
enum class ConflictPolicy {
    /** Leave the existing memento untouched and do not import the incoming one. */
    SkipExisting,

    /** Replace the existing memento with the incoming one (same id). */
    Overwrite,

    /** Import the incoming memento under a freshly generated id (and remapped media ids). */
    KeepBothWithNewId,
}

/** Outcome of a single [ZipImportEngine.import] call. */
data class ImportReport(
    val imported: Int,
    val skipped: Int,
    val mediaRestored: Int,
    val errors: List<String>,
)

/**
 * Restores archives produced by [ZipExportEngine] into a [MementoRepository] and [AssetStore].
 *
 * Media is re-persisted through [AssetStore.saveMedia], which is the only way the storage
 * contract exposes writes; the freshly assigned reference id is therefore written back onto the
 * restored memento so references stay resolvable.
 */
class ZipImportEngine(
    private val mementoRepository: MementoRepository,
    private val assetStore: AssetStore,
) {

    suspend fun import(
        bytes: ByteArray,
        conflict: ConflictPolicy = ConflictPolicy.SkipExisting,
    ): Result<ImportReport> = runCatching {
        val entries = ZipReader.read(bytes)
        val jsonBytes = entries[ZipExportEngine.MEMENTOS_JSON]
            ?: throw IllegalArgumentException("Archive is missing ${ZipExportEngine.MEMENTOS_JSON}")

        val backup = try {
            MementoBackupJson.decodeFromString(MementoBackupV1.serializer(), jsonBytes.decodeToString())
        } catch (error: SerializationException) {
            throw IllegalArgumentException("Invalid ${ZipExportEngine.MEMENTOS_JSON}: ${error.message}", error)
        }

        if (backup.schemaVersion > MementoBackupV1.SCHEMA_VERSION) {
            throw UnsupportedOperationException(
                "Unsupported schema_version ${backup.schemaVersion}: this importer supports " +
                    "schema_version <= ${MementoBackupV1.SCHEMA_VERSION}"
            )
        }

        var imported = 0
        var skipped = 0
        var mediaRestored = 0
        val errors = mutableListOf<String>()

        for (incoming in backup.mementos) {
            try {
                val originalId = MementoId(incoming.id)
                val exists = mementoRepository.getMemento(originalId) != null

                val targetId = when {
                    !exists -> originalId
                    conflict == ConflictPolicy.SkipExisting -> {
                        skipped++
                        continue
                    }
                    conflict == ConflictPolicy.Overwrite -> originalId
                    else -> MementoId(randomId("memento"))
                }

                val domain = incoming.toMemento()
                val restoredMedia = domain.media.map { reference ->
                    restoreMedia(entries, reference, incoming.id, errors)
                        ?.let { saved ->
                            mediaRestored++
                            reference.copy(id = saved.id)
                        }
                        ?: reference
                }

                mementoRepository.saveMemento(domain.copy(id = targetId, media = restoredMedia))
                    .onSuccess { imported++ }
                    .onFailure { errors += "Failed to save memento ${incoming.id}: ${it.message}" }
            } catch (error: Exception) {
                errors += "Invalid memento ${incoming.id}: ${error.message}"
            }
        }

        ImportReport(imported = imported, skipped = skipped, mediaRestored = mediaRestored, errors = errors)
    }

    private suspend fun restoreMedia(
        entries: Map<String, ByteArray>,
        reference: MediaReference,
        mementoId: String,
        errors: MutableList<String>,
    ): MediaReference? {
        val entry = entries[ZipExportEngine.MEDIA_PREFIX + reference.id.value]
        if (entry == null) {
            errors += "Missing media/${reference.id.value} for memento $mementoId"
            return null
        }
        return assetStore.saveMedia(entry, reference.mimeType, reference.id.value).getOrElse { error ->
            errors += "Failed to restore media ${reference.id.value}: ${error.message}"
            null
        }
    }
}
