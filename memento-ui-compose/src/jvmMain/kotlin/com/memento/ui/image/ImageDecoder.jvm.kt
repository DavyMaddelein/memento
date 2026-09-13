package com.memento.ui.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

actual fun decodeImage(bytes: ByteArray): ImageBitmap? = try {
    if (bytes.isEmpty()) {
        null
    } else {
        ImageIO.read(ByteArrayInputStream(bytes))?.toComposeImageBitmap()
    }
} catch (t: Throwable) {
    null
}
