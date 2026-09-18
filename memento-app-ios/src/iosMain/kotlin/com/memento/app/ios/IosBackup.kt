@file:OptIn(kotlinx.cinterop.BetaInteropApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package com.memento.app.ios

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import com.memento.platform.ios.topViewController
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.getBytes
import platform.Foundation.writeToURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerMode
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.popoverPresentationController
import platform.darwin.NSObject

/** Strong reference: the document picker's `delegate` is weak, so the delegate must be retained. */
private var activePickerDelegate: NSObject? = null

/**
 * Writes [bytes] to a temporary `.zip` file and presents the system share sheet for it.
 *
 * Presented on the main queue; returns once the sheet is on screen (not when it is dismissed).
 */
suspend fun shareZip(bytes: ByteArray, fileName: String): Result<Unit> = withContext(Dispatchers.Main) {
    runCatching {
        val url = NSURL.fileURLWithPath(NSTemporaryDirectory() + fileName)
        val data = bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        }
        data.writeToURL(url, atomically = true)

        val controller = UIActivityViewController(activityItems = listOf(url), applicationActivities = null)
        val presenter = topViewController() ?: error("No view controller to present from")
        // Required on iPad so the sheet is anchored; harmless on iPhone.
        controller.popoverPresentationController?.let { popover ->
            popover.sourceView = presenter.view
            popover.sourceRect = presenter.view.bounds
        }
        presenter.presentViewController(controller, animated = true, completion = null)
    }
}

/** Presents a document picker for a `.zip` archive and returns the picked file's bytes. */
suspend fun pickZipFile(): Result<ByteArray> = try {
    Result.success(awaitZipFile())
} catch (throwable: Throwable) {
    Result.failure(throwable)
}

private suspend fun awaitZipFile(): ByteArray = suspendCancellableCoroutine { continuation ->
    val controller = UIDocumentPickerViewController(
        documentTypes = listOf("public.zip-archive", "public.data"),
        inMode = UIDocumentPickerMode.UIDocumentPickerModeOpen,
    )
    val delegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
        override fun documentPicker(
            controller: UIDocumentPickerViewController,
            didPickDocumentsAtURLs: List<*>,
        ) {
            activePickerDelegate = null
            val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
            if (url == null) {
                if (continuation.isActive) {
                    continuation.resumeWithException(IllegalStateException("No file was selected"))
                }
                return
            }
            val accessed = url.startAccessingSecurityScopedResource()
            try {
                val data = NSData.dataWithContentsOfURL(url)
                if (!continuation.isActive) {
                    // Cancelled while reading; nothing to resume.
                } else if (data == null) {
                    continuation.resumeWithException(IllegalStateException("Could not read the selected file"))
                } else {
                    continuation.resume(data.toByteArray())
                }
            } finally {
                if (accessed) url.stopAccessingSecurityScopedResource()
            }
        }

        override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
            activePickerDelegate = null
            if (continuation.isActive) {
                continuation.resumeWithException(IllegalStateException("Import was cancelled"))
            }
        }
    }
    activePickerDelegate = delegate
    controller.delegate = delegate

    val presenter = topViewController()
    if (presenter == null) {
        activePickerDelegate = null
        if (continuation.isActive) {
            continuation.resumeWithException(IllegalStateException("No view controller to present from"))
        }
        return@suspendCancellableCoroutine
    }
    presenter.presentViewController(controller, animated = true, completion = null)
    continuation.invokeOnCancellation {
        controller.dismissViewControllerAnimated(true, null)
        activePickerDelegate = null
    }
}

private fun NSData.toByteArray(): ByteArray {
    val length = this.length.toInt()
    if (length == 0) return ByteArray(0)
    val bytes = ByteArray(length)
    bytes.usePinned { pinned -> this.getBytes(pinned.addressOf(0), this.length) }
    return bytes
}
