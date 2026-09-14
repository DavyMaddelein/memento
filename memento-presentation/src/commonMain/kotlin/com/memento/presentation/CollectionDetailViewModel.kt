package com.memento.presentation

import com.memento.domain.collection.Achievement
import com.memento.domain.collection.CollectionAchievementBoard
import com.memento.domain.collection.CollectionProgress
import com.memento.domain.collection.achievementBoard
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
    val board: CollectionAchievementBoard? = null,
    val recentlyEarned: List<Achievement> = emptyList(),
    val hideCompleted: Boolean = false,
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

    private val knownEarnedIds = mutableSetOf<String>()
    private var earnedIdsInitialised = false

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
                val board = collection?.achievementBoard(mementos)
                var recentlyEarned: List<Achievement> = emptyList()
                if (board != null) {
                    if (!earnedIdsInitialised) {
                        knownEarnedIds.addAll(board.achievements.filter { it.earned }.map { it.id })
                        earnedIdsInitialised = true
                    } else {
                        val newlyEarned = board.achievements.filter { it.earned && it.id !in knownEarnedIds }
                        knownEarnedIds.addAll(newlyEarned.map { it.id })
                        recentlyEarned = newlyEarned
                    }
                }
                CollectionProgressUiState(
                    collection = collection,
                    progress = collection?.progress(mementos),
                    isLoading = false,
                    board = board,
                    recentlyEarned = recentlyEarned,
                    hideCompleted = _state.value.hideCompleted,
                )
            }.collect { _state.value = it }
        }
    }

    fun selectCollection(collectionId: CollectionId) {
        selectedCollectionId.value = collectionId
    }

    fun onToggleHideCompleted() {
        _state.value = _state.value.copy(hideCompleted = !_state.value.hideCompleted)
    }

    /** Clears the queue of achievements earned since the last acknowledgement. */
    fun acknowledgeEarned() {
        if (_state.value.recentlyEarned.isNotEmpty()) {
            _state.value = _state.value.copy(recentlyEarned = emptyList())
        }
    }

    fun dispose() {
        scope.cancel()
    }
}
