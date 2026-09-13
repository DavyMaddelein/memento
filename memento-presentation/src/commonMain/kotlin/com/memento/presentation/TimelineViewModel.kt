package com.memento.presentation

import com.memento.domain.model.CollectionId
import com.memento.domain.model.Memento
import com.memento.domain.model.MementoId
import com.memento.domain.usecase.FilterMementosUseCase
import com.memento.domain.usecase.MementoFilter
import com.memento.domain.usecase.MementoSort
import com.memento.storage.contract.MementoRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Immutable timeline screen state.
 *
 * [mementos] is the filtered + sorted projection of the repository; [allTags] always reflects the
 * distinct tags across *all* mementos so the tag filter chips stay stable while filtering.
 */
data class TimelineUiState(
    val mementos: List<Memento> = emptyList(),
    val query: String = "",
    val selectedTags: Set<String> = emptySet(),
    val selectedCollectionId: CollectionId? = null,
    val sort: MementoSort = MementoSort.NEWEST_FIRST,
    val isLoading: Boolean = true,
    val allTags: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = !isLoading && mementos.isEmpty()
}

/**
 * MVI ViewModel for the keepsake timeline. Observes [MementoRepository] reactively and recomputes
 * the visible list whenever the repository, query, tags, collection or sort changes.
 */
class TimelineViewModel(
    private val mementoRepository: MementoRepository,
    private val filterMementos: FilterMementosUseCase = FilterMementosUseCase(),
    private val scope: CoroutineScope = defaultViewModelScope(),
) {
    private val _state = MutableStateFlow(TimelineUiState())
    val state: StateFlow<TimelineUiState> = _state.asStateFlow()

    private var allMementos: List<Memento> = emptyList()

    init {
        scope.launch {
            mementoRepository.observeAllMementos().collect { mementos ->
                allMementos = mementos
                recompute { it.copy(isLoading = false) }
            }
        }
    }

    fun onSearchQueryChanged(query: String): Unit = recompute { it.copy(query = query) }

    fun onFilterTagToggled(tag: String): Unit = recompute { current ->
        val tags = current.selectedTags
        current.copy(selectedTags = if (tag in tags) tags - tag else tags + tag)
    }

    fun onCollectionSelected(collectionId: CollectionId?): Unit =
        recompute { it.copy(selectedCollectionId = collectionId) }

    fun onSortOrderSelected(sort: MementoSort): Unit = recompute { it.copy(sort = sort) }

    fun onDeleteMemento(id: MementoId) {
        scope.launch { mementoRepository.deleteMemento(id) }
    }

    fun dispose() {
        scope.cancel()
    }

    private fun recompute(transform: (TimelineUiState) -> TimelineUiState) {
        val current = transform(_state.value)
        _state.value = current.copy(
            mementos = applyFilters(allMementos, current),
            allTags = distinctTags(allMementos),
        )
    }

    private fun applyFilters(source: List<Memento>, current: TimelineUiState): List<Memento> {
        val tagFiltered = if (current.selectedTags.isEmpty()) {
            source
        } else {
            source.filter { memento ->
                current.selectedTags.all { tag ->
                    memento.tags.any { it.value.equals(tag, ignoreCase = true) }
                }
            }
        }
        return filterMementos(
            tagFiltered,
            MementoFilter(
                collectionId = current.selectedCollectionId,
                query = current.query,
                sort = current.sort,
            ),
        )
    }

    private fun distinctTags(mementos: List<Memento>): List<String> =
        mementos
            .flatMap { it.tags }
            .map { it.value }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }
}
