package com.memento.presentation

import com.memento.domain.places.PlaceGroup
import com.memento.domain.places.groupMementosByPlace
import com.memento.storage.contract.MementoRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Immutable Places screen state. [groups] is the place-grouped projection of the repository.
 */
data class PlacesUiState(
    val groups: List<PlaceGroup> = emptyList(),
    val isLoading: Boolean = true,
) {
    val isEmpty: Boolean get() = !isLoading && groups.isEmpty()
}

/**
 * MVI ViewModel for the keepsake Places view. Observes [MementoRepository] reactively and regroups
 * mementos by place whenever the repository changes.
 */
class PlacesViewModel(
    private val mementoRepository: MementoRepository,
    private val scope: CoroutineScope = defaultViewModelScope(),
) {
    private val _state = MutableStateFlow(PlacesUiState())
    val state: StateFlow<PlacesUiState> = _state.asStateFlow()

    init {
        scope.launch {
            mementoRepository.observeAllMementos().collect { mementos ->
                _state.value = PlacesUiState(
                    groups = mementos.groupMementosByPlace(),
                    isLoading = false,
                )
            }
        }
    }

    fun dispose() {
        scope.cancel()
    }
}
