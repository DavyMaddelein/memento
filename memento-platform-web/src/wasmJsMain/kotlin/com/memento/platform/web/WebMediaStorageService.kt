package com.memento.platform.web

import com.memento.domain.model.MediaId
import com.memento.domain.model.MediaReference
import com.memento.domain.util.randomId
import com.memento.platform.contract.MediaStorageService
import kotlinx.datetime.Clock

/**
 * [MediaStorageService] backed by IndexedDB.
 *
 * Database `memento`, object store `media`. Each entry is the record
 * `{ id, mimeType, fileName, bytes: Uint8Array }` stored under an out-of-line key equal to the
 * media id. The database handle is shared through [MementoIndexedDb] and opened lazily.
 */
class WebMediaStorageService : MediaStorageService {

    override suspend fun saveMedia(
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
    ): Result<MediaReference> = runCatching {
        val id = MediaId(randomId("media"))
        val record = createMediaRecord(
            id = id.value,
            mimeType = mimeType,
            fileName = fileName,
            bytes = bytes.toWasmUint8Array(),
        )
        writeStore().put(record, id.value).awaitResult()
        MediaReference(
            id = id,
            uri = "indexeddb://${id.value}",
            mimeType = mimeType,
            capturedAt = Clock.System.now(),
        )
    }

    override suspend fun readMedia(reference: MediaReference): Result<ByteArray> = runCatching {
        val result = objectStore().get(reference.id.value).awaitResult()
            ?: throw NoSuchElementException("No media stored for ${reference.id.value}")
        val record = result as StoredMediaRecord
        record.bytes.toWasmByteArray()
    }

    /** Deletes the media record, if present. Not part of [MediaStorageService]. */
    suspend fun deleteMedia(id: MediaId): Result<Unit> = runCatching {
        writeStore().delete(id.value).awaitResult()
    }

    private suspend fun objectStore(): WasmIdbObjectStore =
        MementoIndexedDb.store(MementoIndexedDb.MEDIA_STORE)

    private suspend fun writeStore(): WasmIdbObjectStore =
        MementoIndexedDb.writeStore(MementoIndexedDb.MEDIA_STORE)
}
