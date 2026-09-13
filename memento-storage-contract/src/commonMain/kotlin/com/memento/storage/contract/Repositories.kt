package com.memento.storage.contract

import com.memento.domain.model.Collection
import com.memento.domain.model.CollectionId
import com.memento.domain.model.MediaId
import com.memento.domain.model.MediaReference
import com.memento.domain.model.Memento
import com.memento.domain.model.MementoId
import kotlinx.coroutines.flow.Flow

/**
 * Read/write access to keepsakes. List reads are reactive: observers receive a new emission
 * after every successful mutation.
 */
interface MementoRepository {
    fun observeAllMementos(): Flow<List<Memento>>

    fun observeMemento(id: MementoId): Flow<Memento?>

    suspend fun getMemento(id: MementoId): Memento?

    suspend fun saveMemento(memento: Memento): Result<Unit>

    suspend fun saveMementos(mementos: List<Memento>): Result<Unit>

    suspend fun deleteMemento(id: MementoId): Result<Unit>

    suspend fun deleteAllMementos(): Result<Unit>
}

interface CollectionRepository {
    fun observeCollections(): Flow<List<Collection>>

    fun observeCollection(id: CollectionId): Flow<Collection?>

    suspend fun getCollection(id: CollectionId): Collection?

    suspend fun saveCollection(collection: Collection): Result<Unit>

    suspend fun deleteCollection(id: CollectionId): Result<Unit>
}

/**
 * Binary media persistence, keyed by [MediaId]. Metadata is carried by [MediaReference].
 */
interface AssetStore {
    suspend fun saveMedia(bytes: ByteArray, mimeType: String, fileName: String): Result<MediaReference>

    suspend fun readMedia(id: MediaId): Result<ByteArray>

    suspend fun deleteMedia(id: MediaId): Result<Unit>

    suspend fun listMedia(): List<MediaId>
}
