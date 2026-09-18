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
import platform.UIKit.UIApplication
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
 * [PhotoPickerService] backed by [UIImagePickerController].
 *
 * Camera captures use the camera source; gallery selections use the photo library (single pick for
 * now). Each picked image is JPEG-encoded and persisted through [mediaStorageService]. User
 * cancellation and unavailable hardware surface as failed [Result]s.
 */
class IosPhotoPickerService(
    private val mediaStorageService: MediaStorageService,
) : PhotoPickerService {

    /** Strong reference: the picker's `delegate` is weak, so the delegate must be retained. */
    private var activeDelegate: NSObject? = null

    override suspend fun launchCamera(): Result<MediaReference> = try {
        val image = pickImage(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera)
        Result.success(persist(image, "camera"))
    } catch (throwable: Throwable) {
        Result.failure(throwable)
    }

    override suspend fun launchGallery(): Result<List<MediaReference>> = try {
        val image = pickImage(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary)
        Result.success(listOf(persist(image, "gallery")))
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
        return presentPicker(sourceType)
    }

    private suspend fun presentPicker(sourceType: UIImagePickerControllerSourceType): UIImage =
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

            val presenter = topViewController()
            if (presenter == null) {
                activeDelegate = null
                if (continuation.isActive) {
                    continuation.resumeWithException(IllegalStateException("No view controller to present from"))
                }
                return@suspendCancellableCoroutine
            }
            presenter.presentViewController(controller, animated = true, completion = null)
            continuation.invokeOnCancellation {
                controller.dismissViewControllerAnimated(true, null)
                activeDelegate = null
            }
        }

    private fun topViewController(): UIViewController? {
        var controller = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (controller?.presentedViewController != null) {
            controller = controller.presentedViewController
        }
        return controller
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
