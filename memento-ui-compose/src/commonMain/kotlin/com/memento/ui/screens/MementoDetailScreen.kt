package com.memento.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.memento.domain.model.Memento
import com.memento.presentation.formatIsoDate
import com.memento.ui.components.LocationBadge
import com.memento.ui.components.PhotoThumbnail
import com.memento.ui.components.StarRatingBar
import com.memento.ui.components.TagChipFlow

/**
 * Read-only detail view for a single keepsake: photo carousel, place, date, rating, notes, tags,
 * price and collection membership. Fully stateless — back, edit and delete are surfaced as
 * callbacks and the delete confirmation is owned locally.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MementoDetailScreen(
    memento: Memento,
    images: List<ImageBitmap?> = emptyList(),
    onBack: () -> Unit = {},
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Keepsake") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(imageVector = Icons.Filled.Edit, contentDescription = "Edit keepsake")
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete keepsake",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PhotoCarousel(images = images)

            Text(
                text = memento.title.ifBlank { "Untitled memento" },
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            LocationBadge(
                place = memento.place,
                coordinates = memento.coordinates,
            )

            Text(
                text = formatIsoDate(memento.occurredAt),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.outline,
            )

            memento.rating?.let { rating ->
                StarRatingBar(rating = rating.stars, starSize = 22.dp)
            }

            memento.priceMinorUnits?.let { amount ->
                Text(
                    text = "$amount ${memento.currencyCode.orEmpty()}".trim(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            val notes = memento.tastingNotes?.text.orEmpty()
            if (notes.isNotBlank()) {
                DetailSection("Tasting notes") {
                    Text(
                        text = notes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (memento.tags.isNotEmpty()) {
                DetailSection("Tags") {
                    TagChipFlow(tags = memento.tags.map { it.value })
                }
            }

            if (memento.collectionIds.isNotEmpty()) {
                Text(
                    text = "Collections: " +
                        memento.collectionIds.joinToString(", ") { it.value },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this keepsake?") },
            text = { Text("This removes the moment and its photos. You can undo right afterwards.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun PhotoCarousel(images: List<ImageBitmap?>, modifier: Modifier = Modifier) {
    if (images.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth().height(200.dp),
            contentAlignment = Alignment.Center,
        ) {
            PhotoThumbnail(
                image = null,
                contentDescription = "No photo",
                size = 160.dp,
            )
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { images.size })
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().height(240.dp),
        ) { page ->
            val image = images[page]
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = "Photo ${page + 1} of ${images.size}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp)),
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    PhotoThumbnail(
                        image = null,
                        contentDescription = "Photo ${page + 1} unavailable",
                        size = 160.dp,
                    )
                }
            }
        }
        if (images.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(images.size) { index ->
                    val selected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailSection(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
        )
        content()
    }
}
