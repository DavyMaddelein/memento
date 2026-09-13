package com.memento.platform.android

import com.memento.domain.model.MediaId
import com.memento.domain.model.MediaReference
import com.memento.domain.util.randomId
import com.memento.platform.contract.MediaStorageService
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock

class AndroidPhotoPickerServiceTest {

    @Test
    fun cameraSavesPickedBytesAndReturnsReference() = runTest {
        val bytes = byteArrayOf(1, 2, 3)
        val launcher = FakeAndroidPhotoLauncher(
            camera = Result.success(PickedBytes(bytes, "image/jpeg", "photo.jpg")),
        )
        val storage = RecordingMediaStorageService()
        val service = AndroidPhotoPickerService(launcher, storage)

        val reference = service.launchCamera().getOrThrow()

        assertEquals("image/jpeg", reference.mimeType)
        assertEquals(1, storage.saved.size)
        assertEquals("photo.jpg", storage.saved.single().fileName)
        assertContentEquals(bytes, storage.saved.single().bytes)
    }

    @Test
    fun gallerySavesEveryPickedItemInOrder() = runTest {
        val first = byteArrayOf(1)
        val second = byteArrayOf(2, 3)
        val launcher = FakeAndroidPhotoLauncher(
            gallery = Result.success(
                listOf(
                    PickedBytes(first, "image/png", "one.png"),
                    PickedBytes(second, "image/png", "two.png"),
                ),
            ),
        )
        val storage = RecordingMediaStorageService()
        val service = AndroidPhotoPickerService(launcher, storage)

        val references = service.launchGallery().getOrThrow()

        assertEquals(2, references.size)
        assertEquals(listOf("one.png", "two.png"), storage.saved.map { it.fileName })
        assertContentEquals(first, storage.saved[0].bytes)
        assertContentEquals(second, storage.saved[1].bytes)
    }

    @Test
    fun cameraLauncherFailureIsPropagatedWithoutSaving() = runTest {
        val failure = IllegalStateException("cancelled")
        val launcher = FakeAndroidPhotoLauncher(camera = Result.failure(failure))
        val storage = RecordingMediaStorageService()
        val service = AndroidPhotoPickerService(launcher, storage)

        val result = service.launchCamera()

        assertTrue(result.isFailure)
        assertEquals(failure, result.exceptionOrNull())
        assertTrue(storage.saved.isEmpty())
    }

    @Test
    fun galleryLauncherFailureIsPropagatedWithoutSaving() = runTest {
        val failure = IllegalStateException("cancelled")
        val launcher = FakeAndroidPhotoLauncher(gallery = Result.failure(failure))
        val storage = RecordingMediaStorageService()
        val service = AndroidPhotoPickerService(launcher, storage)

        val result = service.launchGallery()

        assertTrue(result.isFailure)
        assertEquals(failure, result.exceptionOrNull())
        assertTrue(storage.saved.isEmpty())
    }

    @Test
    fun storageFailureIsPropagated() = runTest {
        val failure = java.io.IOException("disk full")
        val launcher = FakeAndroidPhotoLauncher(
            camera = Result.success(PickedBytes(byteArrayOf(9), "image/jpeg", "photo.jpg")),
        )
        val storage = RecordingMediaStorageService(saveFailure = failure)
        val service = AndroidPhotoPickerService(launcher, storage)

        val result = service.launchCamera()

        assertTrue(result.isFailure)
        assertEquals(failure, result.exceptionOrNull())
    }
}

private class FakeAndroidPhotoLauncher(
    var camera: Result<PickedBytes> = Result.failure(UnsupportedOperationException()),
    var gallery: Result<List<PickedBytes>> = Result.failure(UnsupportedOperationException()),
) : AndroidPhotoLauncher {
    override suspend fun pickFromCamera(): Result<PickedBytes> = camera

    override suspend fun pickFromGallery(): Result<List<PickedBytes>> = gallery
}

private class RecordingMediaStorageService(
    private val saveFailure: Throwable? = null,
) : MediaStorageService {

    data class Saved(val bytes: ByteArray, val fileName: String, val mimeType: String)

    val saved = mutableListOf<Saved>()

    override suspend fun saveMedia(
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
    ): Result<MediaReference> {
        saved += Saved(bytes, fileName, mimeType)
        saveFailure?.let { return Result.failure(it) }
        return Result.success(
            MediaReference(
                id = MediaId(randomId("media")),
                uri = "memory://$fileName",
                mimeType = mimeType,
                capturedAt = Clock.System.now(),
            ),
        )
    }

    override suspend fun readMedia(reference: MediaReference): Result<ByteArray> =
        Result.failure(UnsupportedOperationException())
}
