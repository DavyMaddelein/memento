package com.memento.platform.ios

import com.memento.domain.model.MediaReference
import com.memento.platform.contract.MediaStorageService
import com.memento.storage.contract.AssetStore

/**
 * Adapts a storage-layer [AssetStore] to the platform [MediaStorageService] contract.
 *
 * The iOS photo picker persists captures through a [MediaStorageService], while export and import
 * use the [AssetStore] directly. Wiring the picker to this adapter keeps both paths on the same
 * backing store, so media captured at record time is included in backups and stays readable.
 */
class AssetStoreMediaStorageService(
    private val assetStore: AssetStore,
) : MediaStorageService {

    override suspend fun saveMedia(
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
    ): Result<MediaReference> = assetStore.saveMedia(bytes, mimeType, fileName)

    override suspend fun readMedia(reference: MediaReference): Result<ByteArray> =
        assetStore.readMedia(reference.id)
}
