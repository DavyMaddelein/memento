package com.memento.app.android

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.memento.domain.collection.KonbiniDrinkChecklist
import com.memento.domain.model.Memento
import com.memento.platform.android.AndroidPhotoPickerService
import com.memento.platform.android.androidReverseGeocodingService
import com.memento.portability.ConflictPolicy
import com.memento.portability.ZipExportEngine
import com.memento.portability.ZipImportEngine
import com.memento.presentation.CollectionDetailViewModel
import com.memento.presentation.PlacesViewModel
import com.memento.presentation.RecordMementoViewModel
import com.memento.presentation.TimelineViewModel
import com.memento.ui.screens.BackupStatus
import com.memento.ui.screens.CollectionDetailScreen
import com.memento.ui.screens.ExportImportDialog
import com.memento.ui.screens.MementoDetailScreen
import com.memento.ui.screens.PlacesScreen
import com.memento.ui.screens.RecordMementoScreen
import com.memento.ui.screens.TimelineScreen
import com.memento.ui.theme.MementoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

/** The shell's destinations. The backup flow is a dialog, not a destination. */
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
 * Single-activity host. Its only responsibilities are to create the [MementoGraph], build the
 * ViewModels, and orchestrate navigation, permissions and system file dialogs. All feature logic
 * lives in the shared presentation layer; all feature UI lives in `:memento-ui-compose`.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MementoTheme {
                MementoApp()
            }
        }
    }
}

/**
 * Wires the shared modules together and renders the shell.
 *
 * Wiring notes:
 * - The graph and ViewModels are created once (the graph is process-scoped; the ViewModels are
 *   `remember`ed) and disposed with the composition.
 * - Photos are captured/selected through [rememberAndroidPhotoLauncher] and persisted by
 *   [AndroidPhotoPickerService].
 * - `CAMERA` + location are requested on first composition through `RequestMultiplePermissions`;
 *   the result feeds [RuntimePermissionState] so the location provider sees the grant.
 * - The camera/photo pickers and export/import file dialogs are Activity Result contracts, bridged
 *   here; the UI only emits intents and renders state.
 */
@Composable
private fun MementoApp() {
    val context = LocalContext.current
    val graph = remember(context) { MementoGraph.get(context) }
    val photoLauncher = rememberAndroidPhotoLauncher()
    val scope = rememberCoroutineScope()

    val timelineViewModel = remember { TimelineViewModel(graph.mementoRepository) }
    val recordViewModel = remember {
        RecordMementoViewModel(
            mementoRepository = graph.mementoRepository,
            assetStore = graph.assetStore,
            locationProvider = graph.locationProvider,
            photoPicker = AndroidPhotoPickerService(photoLauncher, graph.mediaStorageService),
            reverseGeocodingService = androidReverseGeocodingService(),
            collectionRepository = graph.collectionRepository,
        )
    }
    val collectionViewModel = remember {
        CollectionDetailViewModel(graph.collectionRepository, graph.mementoRepository)
    }
    val placesViewModel = remember { PlacesViewModel(graph.mementoRepository) }

    DisposableEffect(Unit) {
        onDispose {
            timelineViewModel.dispose()
            recordViewModel.dispose()
            collectionViewModel.dispose()
            placesViewModel.dispose()
        }
    }

    // Seed the flagship checklist once, then select it so the collection screen has content.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            if (graph.collectionRepository.getCollection(KonbiniDrinkChecklist.COLLECTION_ID) == null) {
                graph.collectionRepository.saveCollection(
                    KonbiniDrinkChecklist.create(Clock.System.now()),
                )
            }
        }
        collectionViewModel.selectCollection(KonbiniDrinkChecklist.COLLECTION_ID)
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result -> graph.permissions.update(result) }

    LaunchedEffect(Unit) {
        permissionsLauncher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
        )
    }

    val timelineState by timelineViewModel.state.collectAsState()
    val recordState by recordViewModel.state.collectAsState()
    val collectionState by collectionViewModel.state.collectAsState()
    val placesState by placesViewModel.state.collectAsState()

    val timelineImages = rememberMementoImages(timelineState.mementos, graph.imageLoader)
    val recordImages by produceState(
        initialValue = List(recordState.media.size) { null as ImageBitmap? },
        key1 = recordState.media.map { it.id.value },
    ) {
        value = graph.imageLoader.load(recordState.media)
    }

    var screen by remember { mutableStateOf<Screen>(Screen.Timeline) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var backupStatus by remember { mutableStateOf<BackupStatus>(BackupStatus.Idle) }
    var pendingExport by remember { mutableStateOf<ByteArray?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(recordState.savedMementoId) {
        if (recordState.savedMementoId != null) {
            screen = Screen.Timeline
            snackbarHostState.showSnackbar("Keepsake saved")
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        val bytes = pendingExport
        pendingExport = null
        if (uri == null || bytes == null) {
            backupStatus = BackupStatus.Idle
        } else {
            scope.launch {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                }
                backupStatus = BackupStatus.Idle
                showBackupDialog = false
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) {
            backupStatus = BackupStatus.Idle
        } else {
            backupStatus = BackupStatus.Importing
            scope.launch {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }
                if (bytes == null) {
                    backupStatus = BackupStatus.Failed("Unable to read the selected archive")
                    return@launch
                }
                val result = withContext(Dispatchers.IO) {
                    ZipImportEngine(graph.mementoRepository, graph.assetStore)
                        .import(bytes, ConflictPolicy.SkipExisting)
                }
                backupStatus = result.fold(
                    onSuccess = { report ->
                        BackupStatus.Imported(
                            imported = report.imported,
                            skipped = report.skipped,
                            mediaRestored = report.mediaRestored,
                        )
                    },
                    onFailure = { error ->
                        BackupStatus.Failed(error.message ?: "Import failed")
                    },
                )
            }
        }
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
                        // Start a fresh form every time Record is opened from the bar.
                        if (screen != Screen.Record) recordViewModel.reset()
                        screen = Screen.Record
                    },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    label = { Text("Record") },
                )
                NavigationBarItem(
                    selected = false,
                    onClick = {
                        backupStatus = BackupStatus.Idle
                        showBackupDialog = true
                    },
                    icon = { Icon(Icons.Filled.Backup, contentDescription = null) },
                    label = { Text("Backup") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (val current = screen) {
                Screen.Timeline -> TimelineScreen(
                    state = timelineState,
                    images = { memento -> timelineImages[memento.id.value].orEmpty() },
                    onSearchQueryChanged = timelineViewModel::onSearchQueryChanged,
                    onTagToggled = timelineViewModel::onFilterTagToggled,
                    onSortSelected = timelineViewModel::onSortOrderSelected,
                        onMementoClick = { memento -> screen = Screen.Detail(memento, DetailOrigin.TIMELINE) },
                        onDeleteMemento = timelineViewModel::onDeleteMemento,
                    onUndoDelete = timelineViewModel::onRestoreMemento,
                    onAddMemento = {
                        recordViewModel.reset()
                        screen = Screen.Record
                    },
                    onOpenPlaces = { screen = Screen.Places },
                    onQuickCapture = {
                        recordViewModel.reset()
                        screen = Screen.Record
                        recordViewModel.onAddFromCamera()
                        recordViewModel.onFetchLocationClicked()
                    },
                )

                Screen.Record -> RecordMementoScreen(
                    state = recordState,
                    images = recordImages,
                    onAddFromCamera = recordViewModel::onAddFromCamera,
                    onAddFromGallery = recordViewModel::onAddFromGallery,
                    onRemovePhoto = recordViewModel::onRemovePhoto,
                    onFetchLocation = recordViewModel::onFetchLocationClicked,
                    onPlaceNameChanged = recordViewModel::onPlaceNameChanged,
                    onBrandChanged = recordViewModel::onBrandChanged,
                    onCityChanged = recordViewModel::onCityChanged,
                    onTitleChanged = recordViewModel::onTitleChanged,
                    onRatingChanged = recordViewModel::onRatingChanged,
                    onNotesChanged = recordViewModel::onNotesChanged,
                    onTagAdded = recordViewModel::onTagAdded,
                    onTagRemoved = recordViewModel::onTagRemoved,
                    onSave = recordViewModel::onSaveClicked,
                    onBack = { screen = Screen.Timeline },
                    onOccurredAtChanged = recordViewModel::onOccurredAtChanged,
                    onPriceChanged = recordViewModel::onPriceChanged,
                    onCurrencyChanged = recordViewModel::onCurrencyChanged,
                    onCollectionToggled = recordViewModel::onCollectionToggled,
                )

                Screen.Collection -> CollectionDetailScreen(
                    state = collectionState,
                    onBack = { screen = Screen.Timeline },
                    onRecordItem = { item ->
                        // Start from a blank form, then pre-fill from the checklist entry; no
                        // memento id is set, so a new keepsake is created rather than overwritten.
                        recordViewModel.reset()
                        recordViewModel.onTitleChanged(item.label)
                        item.brand?.let(recordViewModel::onBrandChanged)
                        screen = Screen.Record
                    },
                    onAcknowledgeEarned = collectionViewModel::acknowledgeEarned,
                )

                Screen.Places -> PlacesScreen(
                    state = placesState,
                    images = { memento -> timelineImages[memento.id.value].orEmpty() },
                        onMementoClick = { memento -> screen = Screen.Detail(memento, DetailOrigin.PLACES) },
                        onBack = { screen = Screen.Timeline },
                )

                is Screen.Detail -> {
                    val memento = current.memento
                    MementoDetailScreen(
                        memento = memento,
                        images = timelineImages[memento.id.value].orEmpty(),
                        onBack = { screen = detailBackDestination(current.from) },
                        onEdit = {
                            recordViewModel.loadForEdit(memento)
                            screen = Screen.Record
                        },
                        onDelete = {
                            timelineViewModel.onDeleteMemento(memento.id)
                            screen = detailBackDestination(current.from)
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = "Keepsake deleted",
                                    actionLabel = "Undo",
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    timelineViewModel.onRestoreMemento(memento)
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    if (showBackupDialog) {
        ExportImportDialog(
            status = backupStatus,
            onExport = {
                backupStatus = BackupStatus.Exporting
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        ZipExportEngine(graph.assetStore).exportAll(graph.mementoRepository)
                    }
                    result.fold(
                        onSuccess = { bytes ->
                            pendingExport = bytes
                            exportLauncher.launch("memento-backup.zip")
                        },
                        onFailure = { error ->
                            backupStatus = BackupStatus.Failed(error.message ?: "Export failed")
                        },
                    )
                }
            },
            onImportRequested = { importLauncher.launch(arrayOf("application/zip")) },
            onImportBytes = {},
            onDismiss = {
                showBackupDialog = false
                backupStatus = BackupStatus.Idle
            },
        )
    }
}

/**
 * Loads and caches decoded bitmaps for the visible [mementos], keyed by memento id. The cache is a
 * snapshot state map, so a card whose image finishes loading recomposes automatically. Reloading
 * only occurs when the set of referenced media changes.
 */
@Composable
private fun rememberMementoImages(
    mementos: List<Memento>,
    loader: MementoImageLoader,
): Map<String, List<ImageBitmap?>> {
    val cache = remember { mutableStateMapOf<String, List<ImageBitmap?>>() }
    val requestKey = mementos.joinToString(separator = "|") { memento ->
        memento.id.value + ":" + memento.media.joinToString(separator = ",") { it.id.value }
    }
    LaunchedEffect(requestKey, loader) {
        mementos.forEach { memento ->
            val cached = cache[memento.id.value]
            if (cached == null || cached.size != memento.media.size) {
                cache[memento.id.value] = loader.load(memento.media)
            }
        }
        // Evict entries no longer referenced by the visible mementos, then enforce a hard cap.
        val referenced = mementos.mapTo(mutableSetOf()) { it.id.value }
        cache.keys.toList().filterNot { it in referenced }.forEach { cache.remove(it) }
        var overflow = cache.size - MAX_CACHED_MEMENTOS
        while (overflow > 0) {
            val oldest = cache.keys.firstOrNull() ?: break
            cache.remove(oldest)
            overflow--
        }
    }
    return cache
}

/** Upper bound on decoded mementos kept alive by the timeline image cache. */
private const val MAX_CACHED_MEMENTOS = 256
