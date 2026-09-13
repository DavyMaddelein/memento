package com.memento.app.android

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.memento.platform.android.AndroidPhotoLauncher
import com.memento.platform.android.PickedBytes
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred

/**
 * Mutable bridge between the Activity Result callbacks (invoked on the main thread) and the
 * suspending [AndroidPhotoLauncher] methods (invoked from a ViewModel coroutine on a background
 * dispatcher).
 *
 * Only one camera and one gallery request can be in flight at a time; a newly started request
 * fails any still-pending one so a cancelled/abandoned capture can never hang the caller forever.
 */
private class PhotoLauncherBridge {
    val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    var camera: CompletableDeferred<Boolean>? = null

    @Volatile
    var gallery: CompletableDeferred<List<Uri>>? = null

    /** Runs [block] on the main looper; the Activity Result API must be driven from there. */
    fun post(block: () -> Unit) {
        mainHandler.post(block)
    }

    fun awaitCamera(): CompletableDeferred<Boolean> =
        CompletableDeferred<Boolean>().also { deferred ->
            camera?.completeExceptionally(IllegalStateException("Superseded by a newer camera request"))
            camera = deferred
        }

    fun awaitGallery(): CompletableDeferred<List<Uri>> =
        CompletableDeferred<List<Uri>>().also { deferred ->
            gallery?.completeExceptionally(IllegalStateException("Superseded by a newer gallery request"))
            gallery = deferred
        }
}

/**
 * Android implementation of [AndroidPhotoLauncher] that owns the `ActivityResultLauncher`
 * instances registered by [rememberAndroidPhotoLauncher].
 *
 * Camera: a temp file is created in `<cacheDir>/photo`, handed to the camera app through a
 * [FileProvider] content Uri, and its bytes are read back on success. The temp file is always
 * deleted once read.
 *
 * Gallery: the system photo picker returns one `Uri` per selection; each is streamed into
 * [PickedBytes] together with its resolved MIME type and display name.
 *
 * Failure semantics:
 * - user cancellation (camera `false`, empty gallery selection) -> `Result.failure`
 * - unreadable content / empty capture -> `Result.failure`
 * - coroutine cancellation of the awaiting caller propagates as normal cancellation, so the
 *   bridge's pending deferred is completed by the supersede logic, not by swallowing the signal.
 */
private class ActivityPhotoLauncher(
    private val context: Context,
    private val authority: String,
    private val bridge: PhotoLauncherBridge,
    private val cameraLauncher: ManagedActivityResultLauncher<Uri, Boolean>,
    private val galleryLauncher: ManagedActivityResultLauncher<PickVisualMediaRequest, List<Uri>>,
) : AndroidPhotoLauncher {

    override suspend fun pickFromCamera(): Result<PickedBytes> {
        val directory = File(context.cacheDir, PHOTO_DIRECTORY).apply { mkdirs() }
        val file = File.createTempFile(FILE_PREFIX, FILE_SUFFIX, directory)
        val outputUri = FileProvider.getUriForFile(context, authority, file)

        val deferred = bridge.awaitCamera()
        bridge.post { cameraLauncher.launch(outputUri) }

        return try {
            val captured = deferred.await()
            if (!captured) {
                Result.failure(IllegalStateException("Camera capture was cancelled"))
            } else {
                val bytes = file.readBytes()
                if (bytes.isEmpty()) {
                    Result.failure(IOException("Captured photo was empty"))
                } else {
                    Result.success(
                        PickedBytes(bytes = bytes, mimeType = JPEG_MIME_TYPE, fileName = file.name),
                    )
                }
            }
        } finally {
            if (file.exists()) file.delete()
        }
    }

    override suspend fun pickFromGallery(): Result<List<PickedBytes>> {
        val deferred = bridge.awaitGallery()
        bridge.post {
            galleryLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        }

        val uris = deferred.await()
        if (uris.isEmpty()) {
            return Result.failure(IllegalStateException("No photos were selected"))
        }
        return runCatching { uris.map(::readPickedBytes) }
    }

    private fun readPickedBytes(uri: Uri): PickedBytes {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("Unable to open the selected photo: $uri")
        return PickedBytes(
            bytes = bytes,
            mimeType = context.contentResolver.getType(uri) ?: JPEG_MIME_TYPE,
            fileName = displayName(uri) ?: uri.lastPathSegment ?: DEFAULT_FILE_NAME,
        )
    }

    private fun displayName(uri: Uri): String? =
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    private companion object {
        const val PHOTO_DIRECTORY = "photo"
        const val FILE_PREFIX = "memento_"
        const val FILE_SUFFIX = ".jpg"
        const val JPEG_MIME_TYPE = "image/jpeg"
        const val DEFAULT_FILE_NAME = "photo"
    }
}

/**
 * Registers the camera and gallery `ActivityResultLauncher`s and returns an [AndroidPhotoLauncher]
 * backed by them. Must be called from composition; the returned instance is stable across
 * recompositions.
 */
@Composable
fun rememberAndroidPhotoLauncher(): AndroidPhotoLauncher {
    val context = LocalContext.current
    val bridge = remember { PhotoLauncherBridge() }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        bridge.camera?.complete(success)
        bridge.camera = null
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris ->
        bridge.gallery?.complete(uris)
        bridge.gallery = null
    }

    return remember(context, bridge, cameraLauncher, galleryLauncher) {
        ActivityPhotoLauncher(
            context = context,
            authority = "${context.packageName}.fileprovider",
            bridge = bridge,
            cameraLauncher = cameraLauncher,
            galleryLauncher = galleryLauncher,
        )
    }
}
