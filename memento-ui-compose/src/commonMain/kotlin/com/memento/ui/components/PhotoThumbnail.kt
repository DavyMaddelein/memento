package com.memento.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * Fixed-size rounded photo tile. Renders [image] cropped to fill, or a tinted placeholder
 * (image icon on [MaterialTheme.colorScheme.surfaceVariant]) when [image] is null/undecodable.
 */
@Composable
fun PhotoThumbnail(
    image: ImageBitmap?,
    contentDescription: String? = null,
    size: Dp = 96.dp,
    cornerRadius: Dp = 12.dp,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Image,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(size / 3),
            )
        }
    }
}

/**
 * Horizontally scrolling strip of [PhotoThumbnail]s. When [onRemove] is supplied each thumbnail
 * gains a small remove button in its top-end corner.
 */
@Composable
fun PhotoStrip(
    images: List<ImageBitmap?>,
    onRemove: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        images.forEachIndexed { index, image ->
            Box {
                PhotoThumbnail(
                    image = image,
                    contentDescription = "Photo ${index + 1}",
                    size = 88.dp,
                    cornerRadius = 12.dp,
                )
                if (onRemove != null) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 4.dp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(24.dp),
                    ) {
                        IconButton(onClick = { onRemove(index) }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Remove photo ${index + 1}",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
