package com.memento.ui.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image

actual fun decodeImage(bytes: ByteArray): ImageBitmap? = try {
    if (bytes.isEmpty()) {
        null
    } else {
        Image.makeFromEncoded(bytes).toComposeImageBitmap()
    }
} catch (t: Throwable) {
    null
}
