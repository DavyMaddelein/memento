package com.memento.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.memento.domain.model.Rating

/**
 * A row of [Rating.MAX] stars. Interactive (tap a star to set 1..5) when [onRatingChanged] is
 * supplied; otherwise read-only. Rating is fully hoisted — this composable holds no state.
 */
@Composable
fun StarRatingBar(
    rating: Int,
    onRatingChanged: ((Int) -> Unit)? = null,
    starSize: Dp = 28.dp,
    modifier: Modifier = Modifier,
) {
    val interactive = onRatingChanged != null
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(Rating.MAX) { index ->
            val starValue = index + 1
            val filled = starValue <= rating
            Icon(
                imageVector = if (filled) Icons.Filled.Star else Icons.Outlined.Star,
                contentDescription = if (interactive) {
                    "Set rating to $starValue"
                } else {
                    "Rated $rating of ${Rating.MAX}"
                },
                tint = if (filled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
                modifier = Modifier
                    .size(starSize)
                    .then(
                        if (onRatingChanged != null) {
                            Modifier.clickable { onRatingChanged(starValue) }
                        } else {
                            Modifier
                        },
                    ),
            )
        }
    }
}
