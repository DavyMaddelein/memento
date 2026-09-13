package com.memento.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import com.memento.domain.collection.CategoryProgress
import com.memento.domain.collection.CollectionProgress
import com.memento.domain.model.ChecklistItem
import com.memento.domain.model.Collection
import com.memento.domain.model.Memento
import com.memento.presentation.CollectionProgressUiState

/**
 * Detail view for a single curated collection: a hero with overall progress followed by an
 * expandable per-category checklist. Stateless — "Record" actions are forwarded via [onRecordItem].
 */
@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionDetailScreen(
    state: CollectionProgressUiState,
    images: (Memento) -> List<ImageBitmap?> = { emptyList() },
    onBack: () -> Unit = {},
    onRecordItem: (ChecklistItem) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(state.collection?.name ?: "Collection") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        val collection = state.collection
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            collection == null -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No collection available.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            else -> {
                CollectionBody(
                    collection = collection,
                    progress = state.progress,
                    onRecordItem = onRecordItem,
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                )
            }
        }
    }
}

private data class CategorySection(
    val id: String,
    val name: String,
    val categoryProgress: CategoryProgress?,
    val items: List<ChecklistItem>,
)

@Composable
private fun CollectionBody(
    collection: Collection,
    progress: CollectionProgress?,
    onRecordItem: (ChecklistItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val completed = progress?.completedItemIds.orEmpty()
    var expandedIds by remember(collection.id) {
        mutableStateOf(collection.categories.map { it.id }.toSet())
    }

    val sections = if (collection.categories.isEmpty()) {
        listOf(
            CategorySection(
                id = "all",
                name = "Items",
                categoryProgress = null,
                items = collection.items,
            ),
        )
    } else {
        collection.categories
            .map { category ->
                CategorySection(
                    id = category.id,
                    name = category.name,
                    categoryProgress = progress?.categories?.firstOrNull { it.categoryId == category.id },
                    items = collection.items.filter { it.categoryId == category.id },
                )
            }
            .filter { it.items.isNotEmpty() }
    }

    val total = progress?.totalItems ?: collection.items.size
    val done = progress?.completedItems ?: 0
    val percentage = progress?.percentage ?: 0

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = collection.name,
            style = MaterialTheme.typography.headlineMedium,
        )
        if (collection.description.isNotBlank()) {
            Text(
                text = collection.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { percentage / 100f },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "$done/$total — $percentage%",
            style = MaterialTheme.typography.labelLarge,
        )
        sections.forEach { section ->
            CategorySectionRow(
                section = section,
                completed = completed,
                expanded = section.id in expandedIds,
                onToggle = {
                    expandedIds = if (section.id in expandedIds) {
                        expandedIds - section.id
                    } else {
                        expandedIds + section.id
                    }
                },
                onRecordItem = onRecordItem,
            )
        }
    }
}

@Composable
private fun CategorySectionRow(
    section: CategorySection,
    completed: Set<String>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onRecordItem: (ChecklistItem) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Collapse ${section.name}" else "Expand ${section.name}",
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = section.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            section.categoryProgress?.let { categoryProgress ->
                Text(
                    text = "${categoryProgress.completed}/${categoryProgress.total}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        if (expanded) {
            section.items.forEach { item ->
                ChecklistItemRow(
                    item = item,
                    completed = item.id in completed,
                    onRecordItem = onRecordItem,
                )
            }
        }
    }
}

@Composable
private fun ChecklistItemRow(
    item: ChecklistItem,
    completed: Boolean,
    onRecordItem: (ChecklistItem) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = if (completed) {
                Icons.Filled.CheckCircle
            } else {
                Icons.Outlined.RadioButtonUnchecked
            },
            contentDescription = if (completed) "Completed" else "Pending",
            tint = if (completed) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            },
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = item.label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { onRecordItem(item) }) {
            Text("Record")
        }
    }
}
