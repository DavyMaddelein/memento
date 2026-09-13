package com.memento.storage.memory

import com.memento.domain.model.Memento
import com.memento.domain.model.MementoId
import com.memento.storage.contract.MementoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Thread-safe in-memory [MementoRepository]. Emits a new list after every successful mutation.
 */
class InMemoryMementoRepository : MementoRepository {
    private val state = MutableStateFlow<Map<MementoId, Memento>>(emptyMap())
    private val mutex = Mutex()

    override fun observeAllMementos(): Flow<List<Memento>> =
        state.map { snapshot -> snapshot.values.sortedByDescending { it.occurredAt } }

    override fun observeMemento(id: MementoId): Flow<Memento?> = state.map { it[id] }

    override suspend fun getMemento(id: MementoId): Memento? = state.value[id]

    override suspend fun saveMemento(memento: Memento): Result<Unit> = runCatching {
        mutex.withLock { state.update { it + (memento.id to memento) } }
    }

    override suspend fun saveMementos(mementos: List<Memento>): Result<Unit> = runCatching {
        mutex.withLock {
            state.update { current -> current + mementos.associateBy { it.id } }
        }
    }

    override suspend fun deleteMemento(id: MementoId): Result<Unit> = runCatching {
        mutex.withLock { state.update { it - id } }
    }

    override suspend fun deleteAllMementos(): Result<Unit> = runCatching {
        mutex.withLock { state.value = emptyMap() }
    }
}
