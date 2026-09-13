package com.memento.storage.sqlite

import app.cash.sqldelight.db.SqlDriver
import com.memento.domain.model.MediaId
import com.memento.domain.model.MediaReference
import com.memento.domain.util.randomId
import com.memento.storage.contract.AssetStore
import com.memento.storage.sqlite.db.MementoDatabase
import com.memento.storage.sqlite.db.MementoQueries
import kotlinx.datetime.Clock

/**
 * SQLite-backed [AssetStore] that stores media bytes directly as a BLOB.
 */
class SQLiteAssetStore(driver: SqlDriver) : AssetStore {
    private val database = MementoDatabase(driver)
    private val queries: MementoQueries = database.mementoQueries

    override suspend fun saveMedia(
        bytes: ByteArray,
        mimeType: String,
        fileName: String,
    ): Result<MediaReference> = runCatching {
        val id = MediaId(randomId("media"))
        val capturedAt = Clock.System.now()
        val uri = "sqlite://media/${id.value}/$fileName"
        queries.upsertAsset(
            id = id.value,
            uri = uri,
            mime_type = mimeType,
            file_name = fileName,
            captured_at = capturedAt.toString(),
            bytes = bytes,
        )
        MediaReference(id = id, uri = uri, mimeType = mimeType, capturedAt = capturedAt)
    }

    override suspend fun readMedia(id: MediaId): Result<ByteArray> {
        val row = queries.selectAssetById(id.value).executeAsOneOrNull()
            ?: return Result.failure(NoSuchElementException("No media for ${id.value}"))
        return Result.success(row.bytes)
    }

    override suspend fun deleteMedia(id: MediaId): Result<Unit> = runCatching {
        queries.deleteAsset(id.value)
    }

    override suspend fun listMedia(): List<MediaId> =
        queries.selectAllAssetIds().executeAsList().map { MediaId(it) }
}
