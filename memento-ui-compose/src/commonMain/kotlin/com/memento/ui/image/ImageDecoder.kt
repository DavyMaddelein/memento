package com.memento.ui.image

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Decodes raw encoded image [bytes] (PNG/JPEG/...) into an [ImageBitmap] for rendering.
 *
 * Returns `null` when the bytes are empty, malformed, or the platform decoder is unavailable.
 * Callers should treat `null` as "show the placeholder" rather than an error.
 */
expect fun decodeImage(bytes: ByteArray): ImageBitmap?
