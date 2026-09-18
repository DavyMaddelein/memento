package com.memento.app.ios

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import com.memento.domain.collection.KonbiniDrinkChecklist
import com.memento.domain.model.Memento
import com.memento.platform.ios.AssetStoreMediaStorageService
import com.memento.platform.ios.IosLocationProvider
import com.memento.platform.ios.IosPhotoPickerService
import com.memento.platform.ios.iosReverseGeocodingService
import com.memento.portability.ConflictPolicy
import com.memento.portability.MediaGarbageCollector
import com.memento.portability.ZipExportEngine
import com.memento.portability.ZipImportEngine
import com.memento.presentation.CollectionDetailViewModel
import com.memento.presentation.PlacesViewModel
import com.memento.presentation.RecordMementoViewModel
import com.memento.presentation.TimelineViewModel
import com.memento.presentation.seedKonbiniCollection
import com.memento.storage.sqlite.DatabaseDriverFactory
import com.memento.storage.sqlite.SQLiteAssetStore
import com.memento.storage.sqlite.SQLiteCollectionRepository
import com.memento.storage.sqlite.SQLiteMementoRepository
import com.memento.ui.image.decodeImage
import com.memento.ui.screens.BackupStatus
import com.memento.ui.screens.CollectionDetailScreen
import com.memento.ui.screens.ExportImportDialog
import com.memento.ui.screens.MementoDetailScreen
import com.memento.ui.screens.PlacesScreen
import com.memento.ui.screens.RecordMementoScreen
import com.memento.ui.screens.TimelineScreen
import com.memento.ui.theme.MementoTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/** The iOS shell's destinations. */
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

/**
 * iOS dependency graph, mirroring the Android [com.memento.app.android.MementoGraph]. Built once per
 * composition; shares a single [CoroutineScope] with the ViewModels so all are cancelled together.
 */
private class IosAppGraph(scope: CoroutineScope) {
    private val driver = DatabaseDriverFactory().createDriver()

    val mementoRepository = SQLiteMementoRepository(driver)
    val collectionRepository = SQLiteCollectionRepository(driver)
    val assetStore = SQLiteAssetStore(driver)
    val mediaStorageService = AssetStoreMediaStorageService(assetStore)

    val locationProvider = IosLocationProvider()
    val photoPicker = IosPhotoPickerService(mediaStorageService)

    val timelineViewModel = TimelineViewModel(mementoRepository, scope = scope)
    val recordViewModel = RecordMementoViewModel(
        mementoRepository = mementoRepository,
        assetStore = assetStore,
        locationProvider = locationProvider,
        photoPicker = photoPicker,
        reverseGeocodingService = iosReverseGeocodingService(),
        collectionRepository = collectionRepository,
        scope = scope,
    )
    val collectionViewModel = CollectionDetailViewModel(collectionRepository, mementoRepository, scope)
    val placesViewModel = PlacesViewModel(mementoRepository, scope)

    val exportEngine = ZipExportEngine(assetStore)
    val importEngine = ZipImportEngine(mementoRepository, assetStore, collectionRepository)
    val mediaGarbageCollector = MediaGarbageCollector(mementoRepository, assetStore)
}

/**
 * Root iOS composable: wires the SQLite-backed graph, seeds the Konbini collection on first launch,
 * and hosts the same screen router used by the other shells.
 */
@Composable
fun App() {
    MementoTheme {
        val scope = rememberCoroutineScope()
        val graph = remember(scope) { IosAppGraph(scope) }

        var screen by remember { mutableStateOf<Screen>(Screen.Timeline) }
        var imageCache by remember { mutableStateOf<Map<String, ImageBitmap?>>(emptyMap()) }
        var showBackup by remember { mutableStateOf(false) }
        var backupStatus by remember { mutableStateOf<BackupStatus>(BackupStatus.Idle) }
        val snackbarHostState = remember { SnackbarHostState() }

        val timelineState by graph.timelineViewModel.state.collectAsState()
        val recordState by graph.recordViewModel.state.collectAsState()
        val collectionState by graph.collectionViewModel.state.collectAsState()
        val placesState by graph.placesViewModel.state.collectAsState()

        DisposableEffect(Unit) {
            onDispose {
                graph.timelineViewModel.dispose()
                graph.recordViewModel.dispose()
                graph.collectionViewModel.dispose()
                graph.placesViewModel.dispose()
            }
        }

        LaunchedEffect(Unit) {
            seedKonbiniCollection(graph.collectionRepository, Clock.System.now())
            graph.collectionViewModel.selectCollection(KonbiniDrinkChecklist.COLLECTION_ID)
            try {
                graph.mediaGarbageCollector.collect()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // Orphan cleanup is best-effort; ignore and continue.
            }
        }

        LaunchedEffect(recordState.savedMementoId) {
            if (recordState.savedMementoId != null) {
                screen = Screen.Timeline
                snackbarHostState.showSnackbar("Memory kept · 思い出")
            }
        }

        val mediaReferences = remember(timelineState.mementos, recordState.media) {
            (timelineState.mementos.flatMap { it.media } + recordState.media).distinctBy { it.id }
        }

        LaunchedEffect(mediaReferences) {
            val missing = mediaReferences.filter { it.id.value !in imageCache }
            val loaded = missing
                .mapNotNull { reference ->
                    graph.assetStore.readMedia(reference.id).getOrNull()?.let { bytes ->
                        reference.id.value to decodeImage(bytes)
                    }
                }
                .toMap()
            val referenced = mediaReferences.mapTo(mutableSetOf()) { it.id.value }
            imageCache = (imageCache + loaded).filterKeys { it in referenced }
        }

        val timelineImages: (Memento) -> List<ImageBitmap?> = { memento ->
            memento.media.map { imageCache[it.id.value] }
        }

        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = screen == Screen.Timeline || screen is Screen.Detail,
                        onClick = { screen = Screen.Timeline },
                        icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                        label = { Text("Timeline") },
                    )
                    NavigationBarItem(
                        selected = screen == Screen.Collection,
                        onClick = { screen = Screen.Collection },
                        icon = { Icon(Icons.Filled.Checklist, contentDescription = null) },
                        label = { Text("Collection") },
                    )
                    NavigationBarItem(
                        selected = screen == Screen.Record,
                        onClick = {
                            if (screen != Screen.Record) graph.recordViewModel.reset()
                            screen = Screen.Record
                        },
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        label = { Text("Record") },
                    )
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
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
                        onOpenBackup = {
                            backupStatus = BackupStatus.Idle
                            showBackup = true
                        },
                        onQuickCapture = {
                            graph.recordViewModel.reset()
                            screen = Screen.Record
                            graph.recordViewModel.onAddFromCamera()
                            graph.recordViewModel.onFetchLocationClicked()
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
                        onOccurredAtChanged = graph.recordViewModel::onOccurredAtChanged,
                        onPriceChanged = graph.recordViewModel::onPriceChanged,
                        onCurrencyChanged = graph.recordViewModel::onCurrencyChanged,
                        onCollectionToggled = graph.recordViewModel::onCollectionToggled,
                        onFlavorTagToggled = graph.recordViewModel::onFlavorTagToggled,
                        onQuickPriceSelected = graph.recordViewModel::onQuickPriceSelected,
                    )

                    Screen.Collection -> CollectionDetailScreen(
                        state = collectionState,
                        images = timelineImages,
                        onBack = { screen = Screen.Timeline },
                        onRecordItem = { item ->
                            graph.recordViewModel.reset()
                            graph.recordViewModel.onTitleChanged(item.label)
                            item.brand?.let(graph.recordViewModel::onBrandChanged)
                            graph.recordViewModel.onCollectionToggled(KonbiniDrinkChecklist.COLLECTION_ID)
                            graph.recordViewModel.onTagAdded(item.id)
                            item.japaneseLabel?.let(graph.recordViewModel::onTagAdded)
                            screen = Screen.Record
                        },
                        onAcknowledgeEarned = graph.collectionViewModel::acknowledgeEarned,
                        onToggleHideCompleted = graph.collectionViewModel::onToggleHideCompleted,
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
                            collectionNames = recordState.availableCollections.associate { it.id.value to it.name },
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
                                        message = "Memory removed",
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
            }
        }

        if (showBackup) {
            ExportImportDialog(
                status = backupStatus,
                onExport = {
                    scope.launch {
                        backupStatus = BackupStatus.Exporting
                        graph.exportEngine
                            .exportAll(graph.mementoRepository, graph.collectionRepository)
                            .fold(
                                onSuccess = { bytes ->
                                    shareZip(bytes, "memento-backup.zip").fold(
                                        onSuccess = { backupStatus = BackupStatus.ExportReady(bytes) },
                                        onFailure = { error ->
                                            backupStatus = BackupStatus.Failed(error.message ?: "Export failed")
                                        },
                                    )
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
                        pickZipFile().fold(
                            onSuccess = { bytes ->
                                graph.importEngine.import(bytes, ConflictPolicy.SkipExisting).fold(
                                    onSuccess = { report ->
                                        backupStatus = BackupStatus.Imported(
                                            imported = report.imported,
                                            skipped = report.skipped,
                                            mediaRestored = report.mediaRestored,
                                            collectionsImported = report.collectionsImported,
                                            errors = report.errors,
                                        )
                                    },
                                    onFailure = { error ->
                                        backupStatus = BackupStatus.Failed(error.message ?: "Import failed")
                                    },
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
