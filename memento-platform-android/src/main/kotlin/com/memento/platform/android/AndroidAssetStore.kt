package com.memento.platform.android

import android.content.Context
import com.memento.domain.model.MediaId
import com.memento.domain.model.MediaReference
import com.memento.domain.util.randomId
import com.memento.storage.contract.AssetStore
import kotlinx.datetime.Clock
import java.io.File

/**
 * [AssetStore] backed by the same `<filesDir>/media` directory used by
 * [AndroidMediaStorageService]. File names are the media ids.
 */
class AndroidAssetStore(context: Context) : AssetStore {

    private val appContext = context.applicationContext

    override suspend fun saveMedia(
        bytes: ByteArray,
        mimeType: String,
        fileName: String,
    ): Result<MediaReference> = runCatching {
        val directory = ensureMediaDirectory(appContext)
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

    override suspend fun readMedia(id: MediaId): Result<ByteArray> = runCatching {
        val file = File(mediaDirectory(appContext), id.value)
        if (!file.exists()) {
            error("No media file for ${id.value}")
        }
        file.readBytes()
    }

    override suspend fun deleteMedia(id: MediaId): Result<Unit> = runCatching {
        val file = File(mediaDirectory(appContext), id.value)
        if (file.exists() && !file.delete()) {
            error("Could not delete media ${id.value}")
        }
    }

    override suspend fun listMedia(): List<MediaId> =
        mediaDirectory(appContext)
            .listFiles()
            ?.asSequence()
            ?.filter { it.isFile }
            ?.map { MediaId(it.name) }
            ?.sortedBy { it.value }
            ?.toList()
            ?: emptyList()
}
