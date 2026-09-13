package com.memento.platform.android

import com.memento.domain.model.MediaReference
import com.memento.platform.contract.MediaStorageService
import com.memento.platform.contract.PhotoPickerService

/**
 * Bytes captured by [AndroidPhotoLauncher], together with the metadata needed to persist them.
 */
data class PickedBytes(
    val bytes: ByteArray,
    val mimeType: String,
    val fileName: String,
)

/**
 * Bridge over the Activity Result API.
 *
 * A plain Android library has no Activity to register `ActivityResultLauncher`s with, so the app
 * layer owns that responsibility and exposes the outcome through this interface. A typical
 * implementation wraps `ActivityResultContracts.TakePicture` / `TakePicturePreview` for
 * [pickFromCamera] and `ActivityResultContracts.PickMultipleVisualMedia` (or `GetContent`) for
 * [pickFromGallery], launching the request and reading the resulting `Uri` into [PickedBytes].
 */
interface AndroidPhotoLauncher {
    suspend fun pickFromCamera(): Result<PickedBytes>

    suspend fun pickFromGallery(): Result<List<PickedBytes>>
}

/**
 * [PhotoPickerService] that turns picked bytes into persisted [MediaReference]s by delegating to
 * [AndroidPhotoLauncher] for the UI and [mediaStorageService] for persistence.
 *
 * Failures from the launcher or the storage layer are propagated unchanged; a partial gallery
 * failure fails the whole operation.
 */
class AndroidPhotoPickerService(
    private val launcher: AndroidPhotoLauncher,
    private val mediaStorageService: MediaStorageService,
) : PhotoPickerService {

    override suspend fun launchCamera(): Result<MediaReference> {
        val picked = launcher.pickFromCamera().getOrElse { return Result.failure(it) }
        return mediaStorageService.saveMedia(picked.bytes, picked.fileName, picked.mimeType)
    }

    override suspend fun launchGallery(): Result<List<MediaReference>> {
        val picked = launcher.pickFromGallery().getOrElse { return Result.failure(it) }
        val references = ArrayList<MediaReference>(picked.size)
        for (item in picked) {
            val reference = mediaStorageService
                .saveMedia(item.bytes, item.fileName, item.mimeType)
                .getOrElse { return Result.failure(it) }
            references += reference
        }
        return Result.success(references)
    }
}
