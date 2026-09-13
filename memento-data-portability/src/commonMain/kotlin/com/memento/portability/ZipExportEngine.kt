package com.memento.portability

import com.memento.domain.model.Memento
import com.memento.storage.contract.AssetStore
import com.memento.storage.contract.MementoRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock

/**
 * Assembles portable export archives.
 *
 * An archive always contains:
 * - `mementos.json` – the versioned [MementoBackupV1] envelope.
 * - `mementos.md` – a derived Markdown journal ([MarkdownJournalGenerator]).
 * - `media/<mediaId>` – one STORED entry per referenced asset that could be read.
 *
 * Missing assets never fail the export: they are skipped and recorded in [lastWarnings].
 */
class ZipExportEngine(private val assetStore: AssetStore) {

    private val warnings = mutableListOf<String>()

    /** Warnings collected during the most recent [export]/[exportAll] call. */
    val lastWarnings: List<String> get() = warnings.toList()

    /** Serializes [mementos] and their available media into a ZIP archive. */
    suspend fun export(mementos: List<Memento>): Result<ByteArray> = runCatching {
        warnings.clear()
        val backup = backupV1(mementos, Clock.System.now())
        val entries = mutableListOf<Pair<String, ByteArray>>()

        entries += MEMENTOS_JSON to MementoBackupJson
            .encodeToString(MementoBackupV1.serializer(), backup)
            .encodeToByteArray()
        entries += MEMENTOS_MD to MarkdownJournalGenerator.generate(backup).encodeToByteArray()

        val seenMedia = mutableSetOf<String>()
        for (memento in mementos) {
            for (reference in memento.media) {
                if (!seenMedia.add(reference.id.value)) continue
                assetStore.readMedia(reference.id)
                    .onSuccess { bytes -> entries += "media/${reference.id.value}" to bytes }
                    .onFailure { error ->
                        warnings += "Skipped missing media ${reference.id.value} " +
                            "(memento ${memento.id.value}): ${error.message}"
                    }
            }
        }

        ZipWriter.write(entries)
    }

    /** Reads every memento from [mementoRepository] and exports them in one archive. */
    suspend fun exportAll(mementoRepository: MementoRepository): Result<ByteArray> = runCatching {
        mementoRepository.observeAllMementos().first()
    }.fold(
        onSuccess = { export(it) },
        onFailure = { Result.failure(it) },
    )

    companion object {
        const val MEMENTOS_JSON = "mementos.json"
        const val MEMENTOS_MD = "mementos.md"
        const val MEDIA_PREFIX = "media/"
    }
}
