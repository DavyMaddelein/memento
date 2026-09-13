package com.memento.platform.contract

import com.memento.domain.model.Coordinates
import com.memento.domain.model.MediaReference

/**
 * Device location capability. Implementations must never throw for expected failures
 * (permission denied, timeout); they return a failed [Result] instead.
 */
interface LocationProvider {
    fun hasPermission(): Boolean

    suspend fun getCurrentCoordinates(): Result<Coordinates>
}

/**
 * Camera and gallery access. Both calls suspend until the user has finished or cancelled.
 */
interface PhotoPickerService {
    suspend fun launchCamera(): Result<MediaReference>

    suspend fun launchGallery(): Result<List<MediaReference>>
}

/**
 * Persists binary media to platform storage and returns a stable reference.
 */
interface MediaStorageService {
    suspend fun saveMedia(bytes: ByteArray, fileName: String, mimeType: String): Result<MediaReference>

    suspend fun readMedia(reference: MediaReference): Result<ByteArray>
}
