package com.memento.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.memento.domain.model.Memento
import com.memento.presentation.formatIsoDate
import com.memento.ui.components.LocationBadge
import com.memento.ui.components.MementoCardShell
import com.memento.ui.components.PhotoThumbnail
import com.memento.ui.components.StarRatingBar
import com.memento.ui.components.TagChipFlow

/**
 * A single keepsake card: leading photo, title, location, date, optional rating, and an expandable
 * "details" section with tasting notes and tags. Fully stateless; photo decoding is injected by the
 * caller through [images].
 */
@Composable
fun MementoCard(
    memento: Memento,
    images: List<ImageBitmap?> = emptyList(),
    onClick: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val notes = memento.tastingNotes?.text.orEmpty()
    val hasNotes = notes.isNotBlank()
    val hasTags = memento.tags.isNotEmpty()
    val hasDetails = hasNotes || hasTags
    val rating = memento.rating

    MementoCardShell(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PhotoThumbnail(
                    image = images.firstOrNull(),
                    contentDescription = memento.title.ifBlank { "Memento photo" },
                    size = 88.dp,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = memento.title.ifBlank { "Untitled memento" },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (onDelete != null) {
                            IconButton(
                                onClick = onDelete,
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Delete memento",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                    LocationBadge(
                        place = memento.place,
                        coordinates = memento.coordinates,
                    )
                    Text(
                        text = formatIsoDate(memento.occurredAt),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    if (rating != null) {
                        StarRatingBar(rating = rating.stars, starSize = 18.dp)
                    }
                }
            }
            if (hasDetails) {
                TextButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(text = if (expanded) "Hide details" else "Show details")
                }
                if (expanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (hasNotes) {
                            Text(
                                text = notes,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (hasTags) {
                            TagChipFlow(tags = memento.tags.map { it.value })
                        }
                    }
                }
            }
        }
    }
}
