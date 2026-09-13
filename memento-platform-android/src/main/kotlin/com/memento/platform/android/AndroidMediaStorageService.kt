package com.memento.platform.android

import android.content.Context
import com.memento.domain.model.MediaId
import com.memento.domain.model.MediaReference
import com.memento.domain.util.randomId
import com.memento.platform.contract.MediaStorageService
import kotlinx.datetime.Clock
import java.io.File

internal const val MEDIA_DIRECTORY = "media"

internal fun mediaDirectory(context: Context): File = File(context.filesDir, MEDIA_DIRECTORY)

internal fun ensureMediaDirectory(context: Context): File {
    val directory = mediaDirectory(context)
    if (!directory.exists() && !directory.mkdirs()) {
        error("Could not create media directory at ${directory.absolutePath}")
    }
    return directory
}

/**
 * File-system backed [MediaStorageService].
 *
 * Bytes are written to `<context.filesDir>/media/<id>`. The returned [MediaReference.uri] uses the
 * `file://` scheme so callers can hand it to platform viewers.
 */
class AndroidMediaStorageService(private val context: Context) : MediaStorageService {

    override suspend fun saveMedia(
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
    ): Result<MediaReference> = runCatching {
        val directory = ensureMediaDirectory(context)
        val id = MediaId(randomId("media"))
        val file = File(directory, id.value)
        file.writeBytes(bytes)
        MediaReference(
            id = id,
            uri = "file://${file.absolutePath}",
            mimeType = mimeType,
            capturedAt = Clock.System.now(),
        )
    }

    override suspend fun readMedia(reference: MediaReference): Result<ByteArray> = runCatching {
        val file = File(mediaDirectory(context), reference.id.value)
        if (!file.exists()) {
            error("No media file for ${reference.id.value}")
        }
        file.readBytes()
    }
}
