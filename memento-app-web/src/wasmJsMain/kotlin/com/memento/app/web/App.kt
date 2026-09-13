package com.memento.app.web

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import com.memento.portability.ConflictPolicy
import com.memento.portability.ZipExportEngine
import com.memento.portability.ZipImportEngine
import com.memento.presentation.CollectionDetailViewModel
import com.memento.presentation.RecordMementoViewModel
import com.memento.presentation.TimelineViewModel
import com.memento.ui.image.decodeImage
import com.memento.ui.screens.BackupStatus
import com.memento.ui.screens.CollectionDetailScreen
import com.memento.ui.screens.ExportImportDialog
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
}

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
        scope = scope,
    )
    val collectionViewModel = CollectionDetailViewModel(
        collectionRepository = collectionRepository,
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
            if (missing.isNotEmpty()) {
                val loaded = missing
                    .mapNotNull { reference ->
                        graph.mediaStorageService.readMedia(reference).getOrNull()?.let { bytes ->
                            reference.id.value to decodeImage(bytes)
                        }
                    }
                    .toMap()
                imageCache = imageCache + loaded
            }
        }

        val timelineImages: (Memento) -> List<ImageBitmap?> = { memento ->
            memento.media.map { imageCache[it.id.value] }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { screen = Screen.Collection }) {
                    Text("Collection")
                }
                TextButton(onClick = { showBackup = true }) {
                    Text("Backup")
                }
            }

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                when (screen) {
                    Screen.Timeline -> TimelineScreen(
                        state = timelineState,
                        images = timelineImages,
                        onSearchQueryChanged = graph.timelineViewModel::onSearchQueryChanged,
                        onTagToggled = graph.timelineViewModel::onFilterTagToggled,
                        onSortSelected = graph.timelineViewModel::onSortOrderSelected,
                        onMementoClick = { memento ->
                            graph.recordViewModel.loadForEdit(memento)
                            screen = Screen.Record
                        },
                        onDeleteMemento = graph.timelineViewModel::onDeleteMemento,
                        onUndoDelete = graph.timelineViewModel::onRestoreMemento,
                        onAddMemento = {
                            graph.recordViewModel.reset()
                            screen = Screen.Record
                        },
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
