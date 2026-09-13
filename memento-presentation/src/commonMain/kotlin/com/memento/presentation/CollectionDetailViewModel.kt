package com.memento.presentation

import com.memento.domain.collection.CollectionProgress
import com.memento.domain.collection.progress
import com.memento.domain.model.Collection
import com.memento.domain.model.CollectionId
import com.memento.storage.contract.CollectionRepository
import com.memento.storage.contract.MementoRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Immutable state for a single collection's checklist progress.
 */
data class CollectionProgressUiState(
    val collection: Collection? = null,
    val progress: CollectionProgress? = null,
    val isLoading: Boolean = true,
)

/**
 * MVI ViewModel observing one collection and the full memento list. Progress recalculates whenever
 * either the collection definition or the memento repository changes.
 *
 * Until [selectCollection] is called the first available collection is shown, so a single-collection
 * repository works without an explicit selection.
 */
class CollectionDetailViewModel(
    private val collectionRepository: CollectionRepository,
    private val mementoRepository: MementoRepository,
    private val scope: CoroutineScope = defaultViewModelScope(),
) {
    private val selectedCollectionId = MutableStateFlow<CollectionId?>(null)
    private val _state = MutableStateFlow(CollectionProgressUiState())
    val state: StateFlow<CollectionProgressUiState> = _state.asStateFlow()

    init {
        scope.launch {
            combine(
                selectedCollectionId,
                collectionRepository.observeCollections(),
                mementoRepository.observeAllMementos(),
            ) { selectedId, collections, mementos ->
                val collection = when {
                    selectedId != null -> collections.firstOrNull { it.id == selectedId }
                    else -> collections.firstOrNull()
                }
                CollectionProgressUiState(
                    collection = collection,
                    progress = collection?.progress(mementos),
                    isLoading = false,
                )
            }.collect { _state.value = it }
        }
    }

    fun selectCollection(collectionId: CollectionId) {
        selectedCollectionId.value = collectionId
    }

    fun dispose() {
        scope.cancel()
    }
}
