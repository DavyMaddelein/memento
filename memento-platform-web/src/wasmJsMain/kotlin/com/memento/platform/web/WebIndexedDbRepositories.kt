package com.memento.platform.web

import com.memento.domain.model.Collection
import com.memento.domain.model.CollectionId
import com.memento.domain.model.Memento
import com.memento.domain.model.MementoId
import com.memento.storage.contract.CollectionRepository
import com.memento.storage.contract.MementoRepository
import kotlin.js.JsArray
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

private val IndexedDbJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * [MementoRepository] persisted in the IndexedDB `mementos` object store.
 *
 * Each record is the JSON encoding of a [Memento] stored under an out-of-line key equal to its id.
 * [observeAllMementos]/[observeMemento] are reactive: the backing [MutableStateFlow] is populated
 * from IndexedDB once at construction and re-read after every successful mutation. All writes hold
 * [mutex], so a save/delete cannot interleave with a reload.
 */
class WebIndexedDbMementoRepository(
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : MementoRepository {

    private val mutex = Mutex()
    private val state = MutableStateFlow<Map<MementoId, Memento>>(emptyMap())
    private val ready = CompletableDeferred<Unit>()

    init {
        scope.launch {
            runCatching { refreshLocked() }
            ready.complete(Unit)
        }
    }

    override fun observeAllMementos(): Flow<List<Memento>> =
        state.map { snapshot -> snapshot.values.sortedByDescending { it.occurredAt } }

    override fun observeMemento(id: MementoId): Flow<Memento?> = state.map { it[id] }

    override suspend fun getMemento(id: MementoId): Memento? {
        awaitReady()
        return state.value[id]
    }

    override suspend fun saveMemento(memento: Memento): Result<Unit> = runCatching {
        awaitReady()
        mutex.withLock {
            putLocked(memento)
            state.value = readAllLocked()
        }
    }

    override suspend fun saveMementos(mementos: List<Memento>): Result<Unit> = runCatching {
        awaitReady()
        mutex.withLock {
            mementos.forEach { putLocked(it) }
            state.value = readAllLocked()
        }
    }

    override suspend fun deleteMemento(id: MementoId): Result<Unit> = runCatching {
        awaitReady()
        mutex.withLock {
            writeStore().delete(id.value).awaitResult()
            state.value = readAllLocked()
        }
    }

    override suspend fun deleteAllMementos(): Result<Unit> = runCatching {
        awaitReady()
        mutex.withLock {
            writeStore().clear().awaitResult()
            state.value = emptyMap()
        }
    }

    private suspend fun putLocked(memento: Memento) {
        writeStore()
            .put(createJsonRecord(IndexedDbJson.encodeToString(Memento.serializer(), memento)), memento.id.value)
            .awaitResult()
    }

    private suspend fun refreshLocked() {
        mutex.withLock { state.value = readAllLocked() }
    }

    private suspend fun readAllLocked(): Map<MementoId, Memento> {
        val result = store().getAll().awaitResult()
        val records = (result as? JsArray<StoredJsonRecord>)?.toList().orEmpty()
        return records
            .mapNotNull { record ->
                runCatching { IndexedDbJson.decodeFromString(Memento.serializer(), record.json) }.getOrNull()
            }
            .associateBy { it.id }
    }

    private suspend fun awaitReady() {
        ready.await()
    }

    private suspend fun store(): WasmIdbObjectStore =
        MementoIndexedDb.store(MementoIndexedDb.MEMENTOS_STORE)

    private suspend fun writeStore(): WasmIdbObjectStore =
        MementoIndexedDb.writeStore(MementoIndexedDb.MEMENTOS_STORE)
}

/**
 * [CollectionRepository] persisted in the IndexedDB `collections` object store. See
 * [WebIndexedDbMementoRepository] for the loading/reactive model.
 */
class WebIndexedDbCollectionRepository(
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : CollectionRepository {

    private val mutex = Mutex()
    private val state = MutableStateFlow<Map<CollectionId, Collection>>(emptyMap())
    private val ready = CompletableDeferred<Unit>()

    init {
        scope.launch {
            runCatching { refreshLocked() }
            ready.complete(Unit)
        }
    }

    override fun observeCollections(): Flow<List<Collection>> =
        state.map { snapshot -> snapshot.values.sortedBy { it.name.lowercase() } }

    override fun observeCollection(id: CollectionId): Flow<Collection?> = state.map { it[id] }

    override suspend fun getCollection(id: CollectionId): Collection? {
        awaitReady()
        return state.value[id]
    }

    override suspend fun saveCollection(collection: Collection): Result<Unit> = runCatching {
        awaitReady()
        mutex.withLock {
            writeStore()
                .put(
                    createJsonRecord(IndexedDbJson.encodeToString(Collection.serializer(), collection)),
                    collection.id.value,
                )
                .awaitResult()
            state.value = readAllLocked()
        }
    }

    override suspend fun deleteCollection(id: CollectionId): Result<Unit> = runCatching {
        awaitReady()
        mutex.withLock {
            writeStore().delete(id.value).awaitResult()
            state.value = readAllLocked()
        }
    }

    private suspend fun refreshLocked() {
        mutex.withLock { state.value = readAllLocked() }
    }

    private suspend fun readAllLocked(): Map<CollectionId, Collection> {
        val result = store().getAll().awaitResult()
        val records = (result as? JsArray<StoredJsonRecord>)?.toList().orEmpty()
        return records
            .mapNotNull { record ->
                runCatching {
                    IndexedDbJson.decodeFromString(Collection.serializer(), record.json)
                }.getOrNull()
            }
            .associateBy { it.id }
    }

    private suspend fun awaitReady() {
        ready.await()
    }

    private suspend fun store(): WasmIdbObjectStore =
        MementoIndexedDb.store(MementoIndexedDb.COLLECTIONS_STORE)

    private suspend fun writeStore(): WasmIdbObjectStore =
        MementoIndexedDb.writeStore(MementoIndexedDb.COLLECTIONS_STORE)
}
