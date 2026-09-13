package com.memento.ui.preview

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.memento.domain.collection.progress
import com.memento.domain.model.ChecklistItem
import com.memento.domain.model.Collection
import com.memento.domain.model.CollectionCategory
import com.memento.domain.model.CollectionId
import com.memento.domain.model.Coordinates
import com.memento.domain.model.MediaId
import com.memento.domain.model.MediaReference
import com.memento.domain.model.Memento
import com.memento.domain.model.MementoId
import com.memento.domain.model.Place
import com.memento.domain.model.Rating
import com.memento.domain.model.Tag
import com.memento.domain.model.TastingNotes
import com.memento.domain.usecase.MementoSort
import com.memento.presentation.CollectionProgressUiState
import com.memento.presentation.RecordMementoUiState
import com.memento.presentation.TimelineUiState
import com.memento.domain.validation.ValidationViolation
import com.memento.ui.screens.BackupStatus
import com.memento.ui.screens.CollectionDetailScreen
import com.memento.ui.screens.ExportImportDialog
import com.memento.ui.screens.MementoCard
import com.memento.ui.screens.RecordMementoScreen
import com.memento.ui.screens.TimelineScreen
import com.memento.ui.theme.MementoTheme
import kotlinx.datetime.Instant
import org.jetbrains.compose.ui.tooling.preview.Preview

private val sampleInstant: Instant = Instant.parse("2026-01-15T08:30:00Z")

private val bossCoffee = Memento(
    id = MementoId("memento-1"),
    title = "Boss Coffee Rainbow Mountain",
    place = Place(name = "7-Eleven Shibuya", brand = "7-Eleven", city = "Tokyo"),
    coordinates = Coordinates(35.6762, 139.6503),
    media = listOf(
        MediaReference(
            id = MediaId("media-1"),
            uri = "file:///photos/boss-coffee.jpg",
            mimeType = "image/jpeg",
            capturedAt = sampleInstant,
        ),
    ),
    rating = Rating(4),
    tastingNotes = TastingNotes(text = "Rich, sweet and surprisingly smooth for a canned coffee."),
    tags = listOf(Tag("coffee"), Tag("konbini")),
    occurredAt = sampleInstant,
    createdAt = sampleInstant,
    updatedAt = sampleInstant,
)

private val strawberryMilk = Memento(
    id = MementoId("memento-2"),
    title = "Strawberry Milk",
    place = Place(name = "Lawson", brand = "Lawson", city = "Kyoto"),
    rating = Rating(5),
    tags = listOf(Tag("drink")),
    occurredAt = Instant.parse("2026-02-02T14:05:00Z"),
    createdAt = sampleInstant,
    updatedAt = sampleInstant,
)

private val sampleCollection = Collection(
    id = CollectionId("collection-1"),
    name = "Japan Konbini Drinks 2026",
    description = "A year of convenience-store discoveries across Japan.",
    categories = listOf(
        CollectionCategory(id = "coffee", name = "Coffee"),
        CollectionCategory(id = "snacks", name = "Snacks"),
    ),
    items = listOf(
        ChecklistItem(id = "boss", label = "Boss Coffee", categoryId = "coffee", brand = "Suntory"),
        ChecklistItem(id = "rainbow", label = "Rainbow Mountain", categoryId = "coffee"),
        ChecklistItem(id = "pocky", label = "Pocky", categoryId = "snacks", brand = "Glico"),
    ),
    createdAt = sampleInstant,
)

@Preview
@Composable
private fun MementoCardPreview() {
    MementoTheme {
        MementoCard(
            memento = bossCoffee,
            onClick = {},
            onDelete = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview
@Composable
private fun TimelineScreenPreview() {
    MementoTheme {
        TimelineScreen(
            state = TimelineUiState(
                mementos = listOf(bossCoffee, strawberryMilk),
                query = "",
                selectedTags = setOf("coffee"),
                sort = MementoSort.NEWEST_FIRST,
                isLoading = false,
                allTags = listOf("coffee", "drink", "konbini"),
            ),
            onSearchQueryChanged = {},
            onTagToggled = {},
            onSortSelected = {},
            onMementoClick = {},
            onDeleteMemento = {},
            onAddMemento = {},
        )
    }
}

@Preview
@Composable
private fun TimelineScreenEmptyPreview() {
    MementoTheme {
        TimelineScreen(
            state = TimelineUiState(isLoading = false),
            onSearchQueryChanged = {},
            onTagToggled = {},
            onSortSelected = {},
            onMementoClick = {},
            onDeleteMemento = {},
            onAddMemento = {},
        )
    }
}

@Preview
@Composable
private fun RecordMementoScreenPreview() {
    MementoTheme {
        RecordMementoScreen(
            state = RecordMementoUiState(
                media = bossCoffee.media,
                coordinates = Coordinates(35.6762, 139.6503),
                placeName = "7-Eleven Shibuya",
                brand = "7-Eleven",
                city = "Tokyo",
                title = "Boss Coffee",
                rating = 4,
                notes = "Rich and smooth.",
                tags = listOf("coffee", "konbini"),
            ),
            onAddFromCamera = {},
            onAddFromGallery = {},
            onRemovePhoto = {},
            onFetchLocation = {},
            onPlaceNameChanged = {},
            onBrandChanged = {},
            onCityChanged = {},
            onTitleChanged = {},
            onRatingChanged = {},
            onNotesChanged = {},
            onTagAdded = {},
            onTagRemoved = {},
            onSave = {},
        )
    }
}

@Preview
@Composable
private fun RecordMementoScreenErrorsPreview() {
    MementoTheme {
        RecordMementoScreen(
            state = RecordMementoUiState(
                title = "",
                errors = listOf(
                    ValidationViolation("title", "A keepsake needs a title or a named place"),
                    ValidationViolation("media", "At least one photo is required"),
                ),
            ),
            onAddFromCamera = {},
            onAddFromGallery = {},
            onRemovePhoto = {},
            onFetchLocation = {},
            onPlaceNameChanged = {},
            onBrandChanged = {},
            onCityChanged = {},
            onTitleChanged = {},
            onRatingChanged = {},
            onNotesChanged = {},
            onTagAdded = {},
            onTagRemoved = {},
            onSave = {},
        )
    }
}

@Preview
@Composable
private fun CollectionDetailScreenPreview() {
    MementoTheme {
        CollectionDetailScreen(
            state = CollectionProgressUiState(
                collection = sampleCollection,
                progress = sampleCollection.progress(listOf(bossCoffee)),
                isLoading = false,
            ),
            onBack = {},
            onRecordItem = {},
        )
    }
}

@Preview
@Composable
private fun ExportImportDialogPreview() {
    MementoTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            ExportImportDialog(
                status = BackupStatus.Imported(imported = 12, skipped = 2, mediaRestored = 8),
                onExport = {},
                onImportRequested = {},
                onImportBytes = {},
                onDismiss = {},
            )
        }
    }
}
