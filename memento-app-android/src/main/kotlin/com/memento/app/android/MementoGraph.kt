package com.memento.app.android

import android.Manifest
import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import com.memento.domain.model.MediaReference
import com.memento.platform.android.AndroidLocationProvider
import com.memento.platform.android.AssetStoreMediaStorageService
import com.memento.platform.contract.MediaStorageService
import com.memento.storage.contract.AssetStore
import com.memento.storage.contract.CollectionRepository
import com.memento.storage.contract.MementoRepository
import com.memento.storage.sqlite.DatabaseDriverFactory
import com.memento.storage.sqlite.SQLiteAssetStore
import com.memento.storage.sqlite.SQLiteCollectionRepository
import com.memento.storage.sqlite.SQLiteMementoRepository
import com.memento.ui.image.decodeImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Process-wide snapshot of runtime permissions.
 *
 * It exists so [AndroidLocationProvider]'s synchronous `permissionChecker` can observe grants made
 * through the Compose `RequestMultiplePermissions` launcher without rebuilding the provider.
 * [update] is called from the launcher callback; [isGranted] is read from the provider.
 */
class RuntimePermissionState {
    private val granted = MutableStateFlow<Set<String>>(emptySet())

    /** Currently granted permission names, exposed as state for optional UI reactions. */
    val grantedPermissions: StateFlow<Set<String>> = granted.asStateFlow()

    fun update(result: Map<String, Boolean>) {
        granted.value = result.filterValues { it }.keys.toSet()
    }

    fun isGranted(permission: String): Boolean = permission in granted.value
}

/**
 * Decodes the photos referenced by a [MediaReference] list into Compose [ImageBitmap]s.
 *
 * The canonical store is the SQLite [AssetStore]; however the Android photo picker persists
 * captures through [AndroidMediaStorageService] (files under `filesDir/media`). Both are consulted
 * so images recorded by either path render: SQLite first, file storage as a fallback. Missing or
 * malformed assets resolve to `null` (the placeholder), never an error.
 */
class MementoImageLoader(
    private val assetStore: AssetStore,
    private val mediaStorageService: MediaStorageService,
) {
    suspend fun load(media: List<MediaReference>): List<ImageBitmap?> = withContext(Dispatchers.IO) {
        media.map { reference ->
            val bytes = assetStore.readMedia(reference.id).getOrNull()
                ?: mediaStorageService.readMedia(reference).getOrNull()
            bytes?.let { decodeImage(it) }
        }
    }
}

/**
 * Application-scoped dependency graph for the Android shell.
 *
 * Built once per process behind [get]: the SQLDelight driver is expensive and must not be
 * re-created on every Activity recreation. It is deliberately composition-free so tests and the
 * Activity can both obtain it. Wiring (view models, launchers, permissions) stays in the UI layer.
 */
class MementoGraph private constructor(context: Context) {

    private val appContext = context.applicationContext

    val permissions = RuntimePermissionState()

    private val driver = DatabaseDriverFactory(appContext).createDriver()

    val mementoRepository: MementoRepository = SQLiteMementoRepository(driver)
    val collectionRepository: CollectionRepository = SQLiteCollectionRepository(driver)
    val assetStore: AssetStore = SQLiteAssetStore(driver)

    /**
     * Photos captured by the picker are persisted through the SQLite [AssetStore] (not the
     * filesystem service) so that exports and imports see exactly the same media the timeline
     * renders.
     */
    val mediaStorageService: MediaStorageService = AssetStoreMediaStorageService(assetStore)

    /** Reads location through the platform provider, gated by [permissions]. */
    val locationProvider = AndroidLocationProvider(
        context = appContext,
        permissionChecker = {
            permissions.isGranted(Manifest.permission.ACCESS_FINE_LOCATION) ||
                permissions.isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)
        },
    )

    val imageLoader = MementoImageLoader(assetStore, mediaStorageService)

    companion object {
        @Volatile
        private var instance: MementoGraph? = null

        /** Returns the process-wide graph, creating it on first access. */
        fun get(context: Context): MementoGraph =
            instance ?: synchronized(this) {
                instance ?: MementoGraph(context).also { instance = it }
            }
    }
}
