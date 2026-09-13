package com.memento.platform.web

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.JsAny
import kotlinx.browser.document
import kotlinx.coroutines.suspendCancellableCoroutine
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint8Array
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLInputElement
import org.w3c.files.File
import org.w3c.files.FileList
import org.w3c.files.FileReader

/** Removes the element with [id] from the document, if present. Useful for tearing down an HTML
 * loading indicator once the Compose canvas is mounted. */
fun removeElement(id: String) {
    val element = document.getElementById(id) ?: return
    element.parentNode?.removeChild(element)
}

/**
 * Triggers a browser download of [bytes] using a transient Blob object URL and an off-screen
 * `<a download>` element. The object URL is revoked once the click has been dispatched.
 */
fun downloadBytes(
    bytes: ByteArray,
    fileName: String,
    mimeType: String = "application/octet-stream",
) {
    val blob = createBlob(bytes.toWasmUint8Array(), mimeType)
    val url = createObjectUrl(blob)
    try {
        val anchor = document.createElement("a") as HTMLAnchorElement
        anchor.href = url
        anchor.download = fileName
        anchor.setAttribute("style", "display:none")
        document.body?.appendChild(anchor)
        anchor.click()
        anchor.parentNode?.removeChild(anchor)
    } finally {
        revokeObjectUrl(url)
    }
}

/**
 * Opens a transient `<input type="file" accept=".zip">`, reads the selected archive as bytes and
 * returns it. Returns `null` when the user cancels (the `change` event fires with no files).
 */
suspend fun pickZipFile(): ByteArray? = suspendCancellableCoroutine { continuation ->
    val input = document.createElement("input") as HTMLInputElement
    input.type = "file"
    input.accept = ".zip,application/zip"
    input.setAttribute("style", "display:none")
    input.addEventListener("change") {
        val file = input.files.firstFile()
        input.parentNode?.removeChild(input)
        if (file == null) {
            if (continuation.isActive) {
                continuation.resume(null)
            }
        } else {
            readFileBytes(
                file = file,
                onSuccess = { bytes -> if (continuation.isActive) continuation.resume(bytes) },
                onFailure = { error ->
                    if (continuation.isActive) continuation.resumeWithException(error)
                },
            )
        }
    }
    document.body?.appendChild(input)
    input.click()
    continuation.invokeOnCancellation { input.parentNode?.removeChild(input) }
}

private fun readFileBytes(
    file: File,
    onSuccess: (ByteArray) -> Unit,
    onFailure: (Throwable) -> Unit,
) {
    val reader = FileReader()
    reader.onload = {
        val result = reader.result
        if (result == null) {
            onFailure(IllegalStateException("Could not read '${file.name}'"))
        } else {
            onSuccess(Uint8Array(result as ArrayBuffer).toWasmByteArray())
        }
    }
    reader.onerror = {
        onFailure(IllegalStateException("Could not read '${file.name}'"))
    }
    reader.readAsArrayBuffer(file)
}

private fun createBlob(array: Uint8Array, mimeType: String): JsAny =
    js("new Blob([array], { type: mimeType })")

private fun createObjectUrl(blob: JsAny): String = js("URL.createObjectURL(blob)")

private fun revokeObjectUrl(url: String): Unit = js("URL.revokeObjectURL(url)")

private fun FileList?.firstFile(): File? {
    if (this == null || length == 0) return null
    return item(0)
}
