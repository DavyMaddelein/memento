package com.memento.storage.memory

import com.memento.domain.model.Collection
import com.memento.domain.model.CollectionId
import com.memento.storage.contract.CollectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InMemoryCollectionRepository : CollectionRepository {
    private val state = MutableStateFlow<Map<CollectionId, Collection>>(emptyMap())
    private val mutex = Mutex()

    override fun observeCollections(): Flow<List<Collection>> =
        state.map { snapshot -> snapshot.values.sortedBy { it.name.lowercase() } }

    override fun observeCollection(id: CollectionId): Flow<Collection?> = state.map { it[id] }

    override suspend fun getCollection(id: CollectionId): Collection? = state.value[id]

    override suspend fun saveCollection(collection: Collection): Result<Unit> = runCatching {
        mutex.withLock { state.update { it + (collection.id to collection) } }
    }

    override suspend fun deleteCollection(id: CollectionId): Result<Unit> = runCatching {
        mutex.withLock { state.update { it - id } }
    }
}
