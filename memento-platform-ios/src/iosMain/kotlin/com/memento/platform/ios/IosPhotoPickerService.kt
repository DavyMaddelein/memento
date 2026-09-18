@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.memento.platform.ios

import com.memento.domain.model.MediaReference
import com.memento.platform.contract.MediaStorageService
import com.memento.platform.contract.PhotoPickerService
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.getBytes
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject

/**
 * [PhotoPickerService] backed by UIKit.
 *
 * Camera captures use [UIImagePickerController]; gallery selection uses [PHPickerViewController]
 * with multi-select enabled. Every picked image is JPEG-encoded and persisted through
 * [mediaStorageService]. User cancellation and unavailable hardware surface as failed [Result]s.
 */
class IosPhotoPickerService(
    private val mediaStorageService: MediaStorageService,
) : PhotoPickerService {

    /** Strong reference: the pickers' `delegate` is weak, so the delegate must be retained. */
    private var activeDelegate: NSObject? = null

    override suspend fun launchCamera(): Result<MediaReference> = try {
        val image = pickImage(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera)
        Result.success(persist(image, "camera"))
    } catch (throwable: Throwable) {
        Result.failure(throwable)
    }

    override suspend fun launchGallery(): Result<List<MediaReference>> = try {
        val images = presentGallery()
        Result.success(images.mapIndexed { index, image -> persist(image, "gallery-$index") })
    } catch (throwable: Throwable) {
        Result.failure(throwable)
    }

    private suspend fun persist(image: UIImage, name: String): MediaReference {
        val bytes = image.toJpegBytes() ?: error("Could not encode the selected photo")
        return mediaStorageService.saveMedia(bytes, "$name.jpg", "image/jpeg").getOrThrow()
    }

    private suspend fun pickImage(sourceType: UIImagePickerControllerSourceType): UIImage {
        if (sourceType == UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera &&
            !UIImagePickerController.isSourceTypeAvailable(sourceType)
        ) {
            error("Camera is not available on this device")
        }
        return presentCamera(sourceType)
    }

    private suspend fun presentCamera(sourceType: UIImagePickerControllerSourceType): UIImage =
        suspendCancellableCoroutine { continuation ->
            val controller = UIImagePickerController().apply { this.sourceType = sourceType }
            val delegate = object :
                NSObject(),
                UIImagePickerControllerDelegateProtocol,
                UINavigationControllerDelegateProtocol {

                override fun imagePickerController(
                    picker: UIImagePickerController,
                    didFinishPickingMediaWithInfo: Map<Any?, *>,
                ) {
                    val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
                    picker.dismissViewControllerAnimated(true, null)
                    activeDelegate = null
                    if (continuation.isActive) {
                        if (image != null) {
                            continuation.resume(image)
                        } else {
                            continuation.resumeWithException(IllegalStateException("No image was selected"))
                        }
                    }
                }

                override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
                    picker.dismissViewControllerAnimated(true, null)
                    activeDelegate = null
                    if (continuation.isActive) {
                        continuation.resumeWithException(IllegalStateException("Photo picking was cancelled"))
                    }
                }
            }
            activeDelegate = delegate
            controller.delegate = delegate

            if (!present(controller)) {
                activeDelegate = null
                if (continuation.isActive) {
                    continuation.resumeWithException(IllegalStateException("No view controller to present from"))
                }
                return@suspendCancellableCoroutine
            }
            continuation.invokeOnCancellation {
                controller.dismissViewControllerAnimated(true, null)
                activeDelegate = null
            }
        }

    private suspend fun presentGallery(): List<UIImage> = suspendCancellableCoroutine { continuation ->
        val configuration = PHPickerConfiguration().apply {
            selectionLimit = 0L
            filter = PHPickerFilter.imagesFilter()
        }
        val controller = PHPickerViewController(configuration = configuration)
        val delegate = object : NSObject(), PHPickerViewControllerDelegateProtocol {
            override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
                picker.dismissViewControllerAnimated(true, null)
                activeDelegate = null
                val results = didFinishPicking.filterIsInstance<PHPickerResult>()
                if (results.isEmpty()) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(IllegalStateException("Photo picking was cancelled"))
                    }
                    return
                }
                loadImages(results) { result ->
                    if (continuation.isActive) {
                        result.fold(
                            onSuccess = { continuation.resume(it) },
                            onFailure = { continuation.resumeWithException(it) },
                        )
                    }
                }
            }
        }
        activeDelegate = delegate
        controller.delegate = delegate

        if (!present(controller)) {
            activeDelegate = null
            if (continuation.isActive) {
                continuation.resumeWithException(IllegalStateException("No view controller to present from"))
            }
            return@suspendCancellableCoroutine
        }
        continuation.invokeOnCancellation {
            controller.dismissViewControllerAnimated(true, null)
            activeDelegate = null
        }
    }

    /** Loads [results] one after another (callback-chained) and reports them as a list. */
    private fun loadImages(
        results: List<PHPickerResult>,
        onComplete: (Result<List<UIImage>>) -> Unit,
    ) {
        val images = ArrayList<UIImage>(results.size)
        fun loadAt(index: Int) {
            if (index >= results.size) {
                onComplete(Result.success(images))
                return
            }
            results[index].itemProvider.loadDataRepresentationForTypeIdentifier("public.image") { data, error ->
                val image = data?.let { UIImage(data = it) }
                when {
                    error != null -> onComplete(
                        Result.failure(IllegalStateException("Could not read a selected photo: ${error.localizedDescription}")),
                    )

                    image == null -> onComplete(
                        Result.failure(IllegalStateException("Could not decode a selected photo")),
                    )

                    else -> {
                        images += image
                        loadAt(index + 1)
                    }
                }
            }
        }
        loadAt(0)
    }

    private fun present(controller: UIViewController): Boolean {
        val presenter = topViewController() ?: return false
        presenter.presentViewController(controller, animated = true, completion = null)
        return true
    }

    private fun UIImage.toJpegBytes(): ByteArray? {
        val data = UIImageJPEGRepresentation(this, 0.9) ?: return null
        val length = data.length.toInt()
        if (length == 0) return ByteArray(0)
        val bytes = ByteArray(length)
        bytes.usePinned { pinned -> data.getBytes(pinned.addressOf(0), data.length) }
        return bytes
    }
}
