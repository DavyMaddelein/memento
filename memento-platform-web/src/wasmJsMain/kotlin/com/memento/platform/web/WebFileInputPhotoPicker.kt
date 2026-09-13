package com.memento.platform.web

import com.memento.domain.model.MediaReference
import com.memento.platform.contract.MediaStorageService
import com.memento.platform.contract.PhotoPickerService
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.browser.document
import kotlinx.coroutines.suspendCancellableCoroutine
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint8Array
import org.w3c.dom.Element
import org.w3c.dom.HTMLInputElement
import org.w3c.files.File
import org.w3c.files.FileList
import org.w3c.files.FileReader

/**
 * [PhotoPickerService] implemented with a transient `<input type="file">` element.
 *
 * The element is appended to the document, clicked, and removed once the `change` event fires.
 * Camera captures use `capture="environment"`; gallery selection allows multiple files. Each picked
 * file is read as an `ArrayBuffer` and persisted through [mediaStorageService].
 */
class WebFileInputPhotoPicker(
    private val mediaStorageService: MediaStorageService,
) : PhotoPickerService {

    override suspend fun launchCamera(): Result<MediaReference> =
        pickFiles(multiple = false, capture = true).mapCatching { references ->
            references.firstOrNull() ?: throw NoSuchElementException("No image was selected")
        }

    override suspend fun launchGallery(): Result<List<MediaReference>> =
        pickFiles(multiple = true, capture = false)

    private suspend fun pickFiles(multiple: Boolean, capture: Boolean): Result<List<MediaReference>> =
        runCatching {
            awaitFileSelection(multiple = multiple, capture = capture).map { file ->
                val bytes = readFileBytes(file)
                mediaStorageService
                    .saveMedia(
                        bytes = bytes,
                        fileName = file.name,
                        mimeType = file.type.ifBlank { DEFAULT_MIME_TYPE },
                    )
                    .getOrThrow()
            }
        }

    private suspend fun awaitFileSelection(multiple: Boolean, capture: Boolean): List<File> =
        suspendCancellableCoroutine { continuation ->
            val input = document.createElement("input") as HTMLInputElement
            input.type = "file"
            input.accept = "image/*"
            input.multiple = multiple
            if (capture) {
                input.setAttribute("capture", "environment")
            }
            input.setAttribute("style", "display:none")
            input.addEventListener("change") {
                val files = input.files.toFiles()
                input.removeFromDom()
                if (continuation.isActive) {
                    continuation.resume(files)
                }
            }
            document.body?.appendChild(input)
            input.click()
            continuation.invokeOnCancellation { input.removeFromDom() }
        }

    private suspend fun readFileBytes(file: File): ByteArray =
        suspendCancellableCoroutine { continuation ->
            val reader = FileReader()
            reader.onload = {
                val result = reader.result
                if (result == null) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            IllegalStateException("Could not read '${file.name}'"),
                        )
                    }
                } else {
                    val buffer = result as ArrayBuffer
                    if (continuation.isActive) {
                        continuation.resume(Uint8Array(buffer).toWasmByteArray())
                    }
                }
            }
            reader.onerror = {
                if (continuation.isActive) {
                    continuation.resumeWithException(
                        IllegalStateException("Could not read '${file.name}'"),
                    )
                }
            }
            reader.readAsArrayBuffer(file)
        }

    private companion object {
        const val DEFAULT_MIME_TYPE = "application/octet-stream"
    }
}

private fun FileList?.toFiles(): List<File> {
    if (this == null) return emptyList()
    return (0 until length).mapNotNull { item(it) }
}

private fun Element.removeFromDom() {
    parentNode?.removeChild(this)
}
