package com.memento.app.web

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.memento.domain.collection.KonbiniDrinkChecklist
import com.memento.domain.model.Memento
import com.memento.platform.web.WebAssetStore
import com.memento.platform.web.WebFileInputPhotoPicker
import com.memento.platform.web.WebGeolocationProvider
import com.memento.platform.web.WebIndexedDbCollectionRepository
import com.memento.platform.web.WebIndexedDbMementoRepository
import com.memento.platform.web.WebMediaStorageService
import com.memento.platform.web.downloadBytes
import com.memento.platform.web.pickZipFile
import com.memento.platform.web.webReverseGeocodingService
import com.memento.portability.ConflictPolicy
import com.memento.portability.ZipExportEngine
import com.memento.portability.ZipImportEngine
import com.memento.presentation.CollectionDetailViewModel
import com.memento.presentation.PlacesViewModel
import com.memento.presentation.RecordMementoViewModel
import com.memento.presentation.TimelineViewModel
import com.memento.ui.image.decodeImage
import com.memento.ui.screens.BackupStatus
import com.memento.ui.screens.CollectionDetailScreen
import com.memento.ui.screens.ExportImportDialog
import com.memento.ui.screens.MementoDetailScreen
import com.memento.ui.screens.PlacesScreen
import com.memento.ui.screens.RecordMementoScreen
import com.memento.ui.screens.TimelineScreen
import com.memento.ui.theme.MementoTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/** Top-level screens reachable from the web shell. */
private sealed interface Screen {
    data object Timeline : Screen
    data object Record : Screen
    data object Collection : Screen
    data object Places : Screen
    data class Detail(val memento: Memento, val from: DetailOrigin) : Screen
}

/** Where a [Screen.Detail] was opened from, so Back/Delete return there. */
private enum class DetailOrigin { TIMELINE, PLACES }

private fun detailBackDestination(origin: DetailOrigin): Screen = when (origin) {
    DetailOrigin.TIMELINE -> Screen.Timeline
    DetailOrigin.PLACES -> Screen.Places
}

/** Upper bound on decoded media kept alive by the web image cache. */
private const val MAX_CACHED_IMAGES = 256

/**
 * Web dependency graph. Constructed once per composition; the [scope] is the remembered
 * composition scope so every ViewModel is cancelled when the app leaves composition.
 */
private class AppGraph(scope: CoroutineScope) {
    val mediaStorageService = WebMediaStorageService()
    val photoPicker = WebFileInputPhotoPicker(mediaStorageService)
    val locationProvider = WebGeolocationProvider()
    val assetStore = WebAssetStore(mediaStorageService)
    val mementoRepository = WebIndexedDbMementoRepository()
    val collectionRepository = WebIndexedDbCollectionRepository()

    val timelineViewModel = TimelineViewModel(mementoRepository, scope = scope)
    val recordViewModel = RecordMementoViewModel(
        mementoRepository = mementoRepository,
        assetStore = assetStore,
        locationProvider = locationProvider,
        photoPicker = photoPicker,
        reverseGeocodingService = webReverseGeocodingService(),
        scope = scope,
    )
    val collectionViewModel = CollectionDetailViewModel(
        collectionRepository = collectionRepository,
        mementoRepository = mementoRepository,
        scope = scope,
    )
    val placesViewModel = PlacesViewModel(
        mementoRepository = mementoRepository,
        scope = scope,
    )

    val exportEngine = ZipExportEngine(assetStore)
    val importEngine = ZipImportEngine(mementoRepository, assetStore)
}

/**
 * Root composable: wires the platform services and ViewModels together, seeds the Konbini
 * collection on first launch, and hosts a tiny screen router plus the backup dialog.
 */
@Composable
fun App() {
    MementoTheme {
        val scope = rememberCoroutineScope()
        val graph = remember(scope) { AppGraph(scope) }

        var screen by remember { mutableStateOf<Screen>(Screen.Timeline) }
        var showBackup by remember { mutableStateOf(false) }
        var backupStatus by remember { mutableStateOf<BackupStatus>(BackupStatus.Idle) }
        var imageCache by remember { mutableStateOf<Map<String, ImageBitmap?>>(emptyMap()) }
        val snackbarHostState = remember { SnackbarHostState() }

        val timelineState by graph.timelineViewModel.state.collectAsState()
        val recordState by graph.recordViewModel.state.collectAsState()
        val collectionState by graph.collectionViewModel.state.collectAsState()
        val placesState by graph.placesViewModel.state.collectAsState()

        LaunchedEffect(Unit) {
            if (graph.collectionRepository.getCollection(KonbiniDrinkChecklist.COLLECTION_ID) == null) {
                graph.collectionRepository.saveCollection(KonbiniDrinkChecklist.create(Clock.System.now()))
            }
            graph.collectionViewModel.selectCollection(KonbiniDrinkChecklist.COLLECTION_ID)
        }

        LaunchedEffect(recordState.savedMementoId) {
            if (recordState.savedMementoId != null) {
                screen = Screen.Timeline
                snackbarHostState.showSnackbar("Keepsake saved")
            }
        }

        val mediaReferences = remember(timelineState.mementos, recordState.media) {
            (timelineState.mementos.flatMap { it.media } + recordState.media).distinctBy { it.id }
        }

        LaunchedEffect(mediaReferences) {
            val missing = mediaReferences.filter { it.id.value !in imageCache }
            val loaded = missing
                .mapNotNull { reference ->
                    graph.mediaStorageService.readMedia(reference).getOrNull()?.let { bytes ->
                        reference.id.value to decodeImage(bytes)
                    }
                }
                .toMap()
            // Keep only media still referenced by the timeline or the record form, then cap size.
            val referenced = mediaReferences.mapTo(mutableSetOf()) { it.id.value }
            var updated = (imageCache + loaded).filterKeys { it in referenced }
            if (updated.size > MAX_CACHED_IMAGES) {
                updated = updated.entries.toList()
                    .takeLast(MAX_CACHED_IMAGES)
                    .associate { it.key to it.value }
            }
            imageCache = updated
        }

        val timelineImages: (Memento) -> List<ImageBitmap?> = { memento ->
            memento.media.map { imageCache[it.id.value] }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            Surface(color = MaterialTheme.colorScheme.background) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TopNavButton("Mementos", selected = screen == Screen.Timeline) {
                        screen = Screen.Timeline
                    }
                    TopNavButton("Places", selected = screen == Screen.Places) {
                        screen = Screen.Places
                    }
                    TopNavButton("Collection", selected = screen == Screen.Collection) {
                        screen = Screen.Collection
                    }
                    TopNavButton("Backup", selected = false) {
                        backupStatus = BackupStatus.Idle
                        showBackup = true
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                when (val current = screen) {
                    Screen.Timeline -> TimelineScreen(
                        state = timelineState,
                        images = timelineImages,
                        onSearchQueryChanged = graph.timelineViewModel::onSearchQueryChanged,
                        onTagToggled = graph.timelineViewModel::onFilterTagToggled,
                        onSortSelected = graph.timelineViewModel::onSortOrderSelected,
                        onMementoClick = { memento -> screen = Screen.Detail(memento, DetailOrigin.TIMELINE) },
                        onDeleteMemento = graph.timelineViewModel::onDeleteMemento,
                        onUndoDelete = graph.timelineViewModel::onRestoreMemento,
                        onAddMemento = {
                            graph.recordViewModel.reset()
                            screen = Screen.Record
                        },
                        onOpenPlaces = { screen = Screen.Places },
                    )

                    Screen.Record -> RecordMementoScreen(
                        state = recordState,
                        images = recordState.media.map { imageCache[it.id.value] },
                        onAddFromCamera = graph.recordViewModel::onAddFromCamera,
                        onAddFromGallery = graph.recordViewModel::onAddFromGallery,
                        onRemovePhoto = graph.recordViewModel::onRemovePhoto,
                        onFetchLocation = graph.recordViewModel::onFetchLocationClicked,
                        onPlaceNameChanged = graph.recordViewModel::onPlaceNameChanged,
                        onBrandChanged = graph.recordViewModel::onBrandChanged,
                        onCityChanged = graph.recordViewModel::onCityChanged,
                        onTitleChanged = graph.recordViewModel::onTitleChanged,
                        onRatingChanged = graph.recordViewModel::onRatingChanged,
                        onNotesChanged = graph.recordViewModel::onNotesChanged,
                        onTagAdded = graph.recordViewModel::onTagAdded,
                        onTagRemoved = graph.recordViewModel::onTagRemoved,
                        onSave = graph.recordViewModel::onSaveClicked,
                        onBack = { screen = Screen.Timeline },
                        onOccurredAtChanged = graph.recordViewModel::onOccurredAtChanged,
                        onPriceChanged = graph.recordViewModel::onPriceChanged,
                        onCurrencyChanged = graph.recordViewModel::onCurrencyChanged,
                    )

                    Screen.Collection -> CollectionDetailScreen(
                        state = collectionState,
                        images = timelineImages,
                        onBack = { screen = Screen.Timeline },
                        onRecordItem = { item ->
                            graph.recordViewModel.reset()
                            graph.recordViewModel.onTitleChanged(item.label)
                            graph.recordViewModel.onBrandChanged(item.brand.orEmpty())
                            graph.recordViewModel.onCollectionToggled(KonbiniDrinkChecklist.COLLECTION_ID)
                            screen = Screen.Record
                        },
                        onAcknowledgeEarned = graph.collectionViewModel::acknowledgeEarned,
                    )

                    Screen.Places -> PlacesScreen(
                        state = placesState,
                        images = timelineImages,
                        onMementoClick = { memento -> screen = Screen.Detail(memento, DetailOrigin.PLACES) },
                        onBack = { screen = Screen.Timeline },
                    )

                    is Screen.Detail -> {
                        val memento = current.memento
                        MementoDetailScreen(
                            memento = memento,
                            images = memento.media.map { imageCache[it.id.value] },
                            onBack = { screen = detailBackDestination(current.from) },
                            onEdit = {
                                graph.recordViewModel.loadForEdit(memento)
                                screen = Screen.Record
                            },
                            onDelete = {
                                graph.timelineViewModel.onDeleteMemento(memento.id)
                                screen = detailBackDestination(current.from)
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Keepsake deleted",
                                        actionLabel = "Undo",
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        graph.timelineViewModel.onRestoreMemento(memento)
                                    }
                                }
                            },
                        )
                    }
                }
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }

        if (showBackup) {
            ExportImportDialog(
                status = backupStatus,
                onExport = {
                    scope.launch {
                        backupStatus = BackupStatus.Exporting
                        val mementos = graph.mementoRepository.observeAllMementos().first()
                        graph.exportEngine.export(mementos).fold(
                            onSuccess = { bytes ->
                                runCatching {
                                    downloadBytes(bytes, "memento-backup.zip", "application/zip")
                                }
                                backupStatus = BackupStatus.ExportReady(bytes)
                            },
                            onFailure = { error ->
                                backupStatus = BackupStatus.Failed(error.message ?: "Export failed")
                            },
                        )
                    }
                },
                onImportRequested = {
                    scope.launch {
                        backupStatus = BackupStatus.Importing
                        val bytes = runCatching { pickZipFile() }.getOrElse { error ->
                            backupStatus = BackupStatus.Failed(error.message ?: "Import failed")
                            null
                        }
                        if (bytes == null) {
                            if (backupStatus == BackupStatus.Importing) {
                                backupStatus = BackupStatus.Idle
                            }
                        } else {
                            graph.importEngine.import(bytes, ConflictPolicy.SkipExisting).fold(
                                onSuccess = { report ->
                                    backupStatus = BackupStatus.Imported(
                                        imported = report.imported,
                                        skipped = report.skipped,
                                        mediaRestored = report.mediaRestored,
                                    )
                                },
                                onFailure = { error ->
                                    backupStatus = BackupStatus.Failed(error.message ?: "Import failed")
                                },
                            )
                        }
                    }
                },
                onImportBytes = { bytes ->
                    scope.launch {
                        backupStatus = BackupStatus.Importing
                        graph.importEngine.import(bytes, ConflictPolicy.SkipExisting).fold(
                            onSuccess = { report ->
                                backupStatus = BackupStatus.Imported(
                                    imported = report.imported,
                                    skipped = report.skipped,
                                    mediaRestored = report.mediaRestored,
                                )
                            },
                            onFailure = { error ->
                                backupStatus = BackupStatus.Failed(error.message ?: "Import failed")
                            },
                        )
                    }
                },
                onDismiss = {
                    showBackup = false
                    backupStatus = BackupStatus.Idle
                },
            )
        }
    }
}

/** Top navigation button that stays legible on the app background. */
@Composable
private fun TopNavButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        ),
    ) {
        Text(
            text = label,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
