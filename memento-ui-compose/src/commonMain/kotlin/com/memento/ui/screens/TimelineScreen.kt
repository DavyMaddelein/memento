package com.memento.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import com.memento.domain.model.Memento
import com.memento.domain.model.MementoId
import com.memento.domain.usecase.MementoSort
import com.memento.presentation.TimelineUiState
import com.memento.ui.components.TagChip
import kotlinx.coroutines.launch

/**
 * The keepsake timeline: search, tag filters, sort control and a lazily rendered list of
 * [MementoCard]s. Stateless — every interaction is forwarded to the caller.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    state: TimelineUiState,
    images: (Memento) -> List<ImageBitmap?> = { emptyList() },
    onSearchQueryChanged: (String) -> Unit,
    onTagToggled: (String) -> Unit,
    onSortSelected: (MementoSort) -> Unit,
    onMementoClick: (Memento) -> Unit,
    onDeleteMemento: (MementoId) -> Unit,
    onUndoDelete: (Memento) -> Unit = {},
    onAddMemento: () -> Unit,
    onOpenPlaces: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var pendingDelete by remember { mutableStateOf<Memento?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Mementos") },
                actions = {
                    if (onOpenPlaces != null) {
                        IconButton(onClick = onOpenPlaces) {
                            Icon(
                                imageVector = Icons.Filled.Place,
                                contentDescription = "Places",
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddMemento) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Add memento")
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onSearchQueryChanged,
                label = { Text("Search mementos") },
                leadingIcon = {
                    Icon(imageVector = Icons.Filled.Search, contentDescription = null)
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (state.allTags.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.allTags.forEach { tag ->
                        TagChip(
                            label = tag,
                            selected = tag in state.selectedTags,
                            onClick = { onTagToggled(tag) },
                        )
                    }
                }
            }
            SortSelector(
                sort = state.sort,
                onSortSelected = onSortSelected,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                state.isEmpty -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No mementos yet. Tap + to record your first one.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(items = state.mementos, key = { it.id.value }) { memento ->
                            MementoCard(
                                memento = memento,
                                images = images(memento),
                                onClick = { onMementoClick(memento) },
                                onDelete = { pendingDelete = memento },
                            )
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { memento ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this keepsake?") },
            text = {
                Text(
                    "This removes the moment and its photos. You can undo right afterwards.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        onDeleteMemento(memento.id)
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = "Keepsake deleted",
                                actionLabel = "Undo",
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                onUndoDelete(memento)
                            }
                        }
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun SortSelector(
    sort: MementoSort,
    onSortSelected: (MementoSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(onClick = { expanded = true }) {
            Text(text = "Sort: ${sort.displayLabel()}")
            Icon(imageVector = Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MementoSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.displayLabel()) },
                    onClick = {
                        onSortSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

internal fun MementoSort.displayLabel(): String = when (this) {
    MementoSort.NEWEST_FIRST -> "Newest first"
    MementoSort.OLDEST_FIRST -> "Oldest first"
    MementoSort.HIGHEST_RATED -> "Highest rated"
    MementoSort.TITLE -> "Title"
}
