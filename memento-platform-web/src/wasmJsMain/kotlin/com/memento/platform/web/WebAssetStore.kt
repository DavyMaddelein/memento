package com.memento.platform.web

import com.memento.domain.model.MediaId
import com.memento.domain.model.MediaReference
import com.memento.platform.contract.MediaStorageService
import com.memento.storage.contract.AssetStore
import kotlinx.datetime.Clock

/**
 * Adapts the platform [MediaStorageService] to the storage-contract [AssetStore] required by the
 * backup engines. Reading is keyed by [MediaId]; because the platform contract only accepts a
 * [MediaReference], a synthetic reference is built from the id (the `media` object store is keyed by
 * id, so the uri/mimeType are irrelevant for retrieval).
 */
class WebAssetStore(
    private val mediaStorageService: WebMediaStorageService,
) : AssetStore {

    override suspend fun saveMedia(
        bytes: ByteArray,
        mimeType: String,
        fileName: String,
    ): Result<MediaReference> =
        mediaStorageService.saveMedia(bytes = bytes, fileName = fileName, mimeType = mimeType)

    override suspend fun readMedia(id: MediaId): Result<ByteArray> =
        mediaStorageService.readMedia(
            MediaReference(
                id = id,
                uri = "indexeddb://${id.value}",
                mimeType = FALLBACK_MIME_TYPE,
                capturedAt = Clock.System.now(),
            ),
        )

    override suspend fun deleteMedia(id: MediaId): Result<Unit> =
        mediaStorageService.deleteMedia(id)

    /** Not supported by the IndexedDB media store; always empty. */
    override suspend fun listMedia(): List<MediaId> = emptyList()

    private companion object {
        const val FALLBACK_MIME_TYPE = "application/octet-stream"
    }
}
