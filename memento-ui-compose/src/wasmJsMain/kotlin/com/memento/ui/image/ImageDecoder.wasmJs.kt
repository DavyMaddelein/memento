package com.memento.ui.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap

actual fun decodeImage(bytes: ByteArray): ImageBitmap? = try {
    if (bytes.isEmpty()) {
        null
    } else {
        org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
    }
} catch (t: Throwable) {
    null
}
