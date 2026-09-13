package com.memento.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.memento.domain.model.Coordinates
import com.memento.domain.model.Place
import kotlin.math.round

/**
 * Map-pin badge describing where a keepsake happened. Prefers structured [place] details
 * (name / brand / city), falls back to raw [coordinates], and finally to a muted "No location".
 */
@Composable
fun LocationBadge(
    place: Place?,
    coordinates: Coordinates? = null,
    modifier: Modifier = Modifier,
) {
    val label = when {
        place != null -> listOfNotNull(
            place.name.takeIf { it.isNotBlank() },
            place.brand?.takeIf { it.isNotBlank() },
            place.city?.takeIf { it.isNotBlank() },
        ).distinct().joinToString(" · ")
        coordinates != null -> formatCoordinates(coordinates)
        else -> "No location"
    }
    val hasLocation = place != null || coordinates != null
    val contentColor = if (hasLocation) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.outline
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = contentColor,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun formatCoordinates(coordinates: Coordinates): String {
    fun format(value: Double): String = (round(value * 10_000.0) / 10_000.0).toString()
    return "${format(coordinates.latitude)}, ${format(coordinates.longitude)}"
}
