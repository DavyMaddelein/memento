package com.memento.storage.memory

import com.memento.domain.model.MediaId
import com.memento.domain.model.MediaReference
import com.memento.domain.util.randomId
import com.memento.storage.contract.AssetStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

/**
 * Non-persistent [AssetStore] for tests and previews. Binary payloads live only for the
 * lifetime of the process.
 */
class InMemoryAssetStore : AssetStore {
    private val mutex = Mutex()
    private val bytesById = mutableMapOf<MediaId, ByteArray>()

    override suspend fun saveMedia(
        bytes: ByteArray,
        mimeType: String,
        fileName: String,
    ): Result<MediaReference> = runCatching {
        val id = MediaId(randomId("media"))
        mutex.withLock { bytesById[id] = bytes }
        MediaReference(
            id = id,
            uri = "memory://$fileName",
            mimeType = mimeType,
            capturedAt = Clock.System.now(),
        )
    }

    override suspend fun readMedia(id: MediaId): Result<ByteArray> {
        val bytes = mutex.withLock { bytesById[id] }
            ?: return Result.failure(NoSuchElementException("No media for ${id.value}"))
        return Result.success(bytes)
    }

    override suspend fun deleteMedia(id: MediaId): Result<Unit> = runCatching {
        mutex.withLock { bytesById.remove(id) }
    }

    override suspend fun listMedia(): List<MediaId> = mutex.withLock { bytesById.keys.toList() }
}
