package com.memento.platform.contract.fakes

import com.memento.domain.model.Coordinates
import com.memento.domain.model.MediaId
import com.memento.domain.model.MediaReference
import com.memento.domain.util.randomId
import com.memento.platform.contract.LocationProvider
import com.memento.platform.contract.MediaStorageService
import com.memento.platform.contract.PhotoPickerService
import kotlinx.datetime.Clock

/**
 * Configurable [LocationProvider] for deterministic tests.
 */
class FakeLocationProvider(
    var permissionGranted: Boolean = true,
    var coordinates: Coordinates? = Coordinates(latitude = 35.6595, longitude = 139.7005, accuracyMeters = 12.0),
    var failure: Throwable? = null,
) : LocationProvider {
    var callCount: Int = 0
        private set

    override fun hasPermission(): Boolean = permissionGranted

    override suspend fun getCurrentCoordinates(): Result<Coordinates> {
        callCount++
        if (!permissionGranted) {
            return Result.failure(IllegalStateException("Location permission not granted"))
        }
        failure?.let { return Result.failure(it) }
        val value = coordinates
            ?: return Result.failure(IllegalStateException("No coordinates configured"))
        return Result.success(value)
    }
}

/**
 * Configurable [PhotoPickerService]. Outgoing results can be set per call.
 */
class FakePhotoPickerService(
    var cameraResult: Result<MediaReference> = Result.success(sampleMediaReference("camera")),
    var galleryResult: Result<List<MediaReference>> = Result.success(
        listOf(sampleMediaReference("gallery")),
    ),
) : PhotoPickerService {
    var cameraCalls: Int = 0
        private set
    var galleryCalls: Int = 0
        private set

    override suspend fun launchCamera(): Result<MediaReference> {
        cameraCalls++
        return cameraResult
    }

    override suspend fun launchGallery(): Result<List<MediaReference>> {
        galleryCalls++
        return galleryResult
    }
}

/**
 * In-memory [MediaStorageService] backed by a map; useful for tests and previews.
 */
class InMemoryMediaStorageService : MediaStorageService {
    private val bytesById = mutableMapOf<MediaId, ByteArray>()

    override suspend fun saveMedia(
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
    ): Result<MediaReference> {
        val id = MediaId(randomId("media"))
        bytesById[id] = bytes
        return Result.success(
            MediaReference(
                id = id,
                uri = "memory://$fileName",
                mimeType = mimeType,
                capturedAt = Clock.System.now(),
            ),
        )
    }

    override suspend fun readMedia(reference: MediaReference): Result<ByteArray> {
        val bytes = bytesById[reference.id]
            ?: return Result.failure(NoSuchElementException("No media for ${reference.id.value}"))
        return Result.success(bytes)
    }
}

fun sampleMediaReference(name: String, mimeType: String = "image/jpeg"): MediaReference = MediaReference(
    id = MediaId(randomId(name)),
    uri = "memory://$name.jpg",
    mimeType = mimeType,
    capturedAt = Clock.System.now(),
)
