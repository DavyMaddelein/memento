package com.memento.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Default scope for a presentation ViewModel.
 *
 * [Dispatchers.Main.immediate] is not available on every supported target (notably wasmJs), so
 * ViewModels default to [Dispatchers.Default]. Callers that need main-thread affinity can inject
 * their own scope.
 */
internal fun defaultViewModelScope(): CoroutineScope =
    CoroutineScope(SupervisorJob() + Dispatchers.Default)
