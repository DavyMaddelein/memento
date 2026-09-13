package com.memento.ui.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.memento.domain.model.Coordinates
import com.memento.domain.model.Place
import com.memento.ui.components.LocationBadge
import com.memento.ui.components.MementoCardShell
import com.memento.ui.components.PhotoStrip
import com.memento.ui.components.PhotoThumbnail
import com.memento.ui.components.StarRatingBar
import com.memento.ui.components.TagChipFlow
import com.memento.ui.theme.MementoTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun MementoThemePreview() {
    MementoTheme {
        MementoCardShell(modifier = Modifier.padding(16.dp)) {
            StarRatingBar(rating = 4)
        }
    }
}

@Preview
@Composable
private fun StarRatingBarPreview() {
    MementoTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StarRatingBar(rating = 3)
            StarRatingBar(rating = 5, onRatingChanged = {})
            StarRatingBar(rating = 0)
        }
    }
}

@Preview
@Composable
private fun TagChipFlowPreview() {
    MementoTheme {
        TagChipFlow(
            tags = listOf("coffee", "sunset", "roadtrip", "ramen", "first-time"),
            selectedTags = setOf("coffee", "ramen"),
            onTagClick = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview
@Composable
private fun LocationBadgePreview() {
    MementoTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LocationBadge(
                place = Place(name = "Kissa", brand = "Blue Bottle", city = "Tokyo"),
            )
            LocationBadge(place = null, coordinates = Coordinates(35.6762, 139.6503))
            LocationBadge(place = null, coordinates = null)
        }
    }
}

@Preview
@Composable
private fun PhotoThumbnailPreview() {
    MementoTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PhotoThumbnail(image = null)
            PhotoStrip(images = listOf(null, null, null), onRemove = {})
        }
    }
}
