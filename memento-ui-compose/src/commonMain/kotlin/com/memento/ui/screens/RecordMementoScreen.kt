package com.memento.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.memento.domain.model.Coordinates
import com.memento.domain.model.MediaId
import com.memento.domain.validation.ValidationViolation
import com.memento.presentation.RecordMementoUiState
import com.memento.ui.components.PhotoStrip
import com.memento.ui.components.StarRatingBar
import com.memento.ui.components.TagChip
import com.memento.ui.components.TagChipFlow

private val QuickBrands = listOf(
    "7-Eleven",
    "Lawson",
    "FamilyMart",
    "MiniStop",
    "Daily Yamazaki",
)

/**
 * The "record a memento" form: photo capture, location, place metadata, rating, notes and tags.
 * Fully stateless; all state lives in [RecordMementoUiState] and every edit is forwarded upstream.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecordMementoScreen(
    state: RecordMementoUiState,
    images: List<ImageBitmap?> = emptyList(),
    onAddPhoto: () -> Unit = {},
    onAddFromCamera: () -> Unit,
    onAddFromGallery: () -> Unit,
    onRemovePhoto: (MediaId) -> Unit,
    onFetchLocation: () -> Unit,
    onPlaceNameChanged: (String) -> Unit,
    onBrandChanged: (String) -> Unit,
    onCityChanged: (String) -> Unit,
    onTitleChanged: (String) -> Unit,
    onRatingChanged: (Int) -> Unit,
    onNotesChanged: (String) -> Unit,
    onTagAdded: (String) -> Unit,
    onTagRemoved: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val errorsByField = state.errors.groupBy { it.field }
    var tagInput by remember { mutableStateOf("") }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Record a Memento") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    val saveEnabled = !state.isSaving && state.savedMementoId == null
                    TextButton(onClick = onSave, enabled = saveEnabled) {
                        when {
                            state.isSaving -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                                Text("Saving…", modifier = Modifier.padding(start = 8.dp))
                            }
                            state.savedMementoId != null -> Text("Saved")
                            else -> Text("Save")
                        }
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
            if (state.isSaving) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Saving…", style = MaterialTheme.typography.bodyMedium)
                }
            }

            SectionLabel("Photos")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onAddFromCamera) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text("Camera", modifier = Modifier.padding(start = 6.dp))
                }
                OutlinedButton(onClick = onAddFromGallery) {
                    Icon(
                        imageVector = Icons.Filled.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text("Gallery", modifier = Modifier.padding(start = 6.dp))
                }
                IconButton(onClick = onAddPhoto) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = "Add photo")
                }
            }
            if (images.isEmpty()) {
                Text(
                    text = "No photos yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                PhotoStrip(
                    images = images,
                    onRemove = { index ->
                        state.media.getOrNull(index)?.let { reference -> onRemovePhoto(reference.id) }
                    },
                )
            }
            FieldErrors(errorsByField["media"])

            SectionLabel("Location")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = onFetchLocation, enabled = !state.isFetchingLocation) {
                    if (state.isFetchingLocation) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("Locating…", modifier = Modifier.padding(start = 8.dp))
                    } else {
                        Icon(
                            imageVector = Icons.Filled.MyLocation,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text("Use my location", modifier = Modifier.padding(start = 6.dp))
                    }
                }
                state.coordinates?.let { coordinates ->
                    Text(
                        text = formatCoordinates(coordinates),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            FieldErrors(errorsByField["location"])

            OutlinedTextField(
                value = state.placeName,
                onValueChange = onPlaceNameChanged,
                label = { Text("Place name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.brand,
                onValueChange = onBrandChanged,
                label = { Text("Brand") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QuickBrands.forEach { brand ->
                    TagChip(
                        label = brand,
                        selected = state.brand.equals(brand, ignoreCase = true),
                        onClick = { onBrandChanged(brand) },
                    )
                }
            }
            OutlinedTextField(
                value = state.city,
                onValueChange = onCityChanged,
                label = { Text("City") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionLabel("Details")
            OutlinedTextField(
                value = state.title,
                onValueChange = onTitleChanged,
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            FieldErrors(errorsByField["title"])

            StarRatingBar(rating = state.rating, onRatingChanged = onRatingChanged)

            OutlinedTextField(
                value = state.notes,
                onValueChange = onNotesChanged,
                label = { Text("Tasting notes") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionLabel("Tags")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = tagInput,
                    onValueChange = { tagInput = it },
                    label = { Text("Add a tag") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        onTagAdded(tagInput)
                        tagInput = ""
                    },
                ) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = "Add tag")
                }
            }
            if (state.tags.isNotEmpty()) {
                TagChipFlow(tags = state.tags, onTagClick = { onTagRemoved(it) })
            }
            FieldErrors(errorsByField["tags"])

            FieldErrors(errorsByField["save"])
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier,
    )
}

@Composable
private fun FieldErrors(
    violations: List<ValidationViolation>?,
    modifier: Modifier = Modifier,
) {
    if (violations.isNullOrEmpty()) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        violations.forEach { violation ->
            Text(
                text = violation.message,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

internal fun formatCoordinates(coordinates: Coordinates): String =
    "${coordinates.latitude}, ${coordinates.longitude}"
