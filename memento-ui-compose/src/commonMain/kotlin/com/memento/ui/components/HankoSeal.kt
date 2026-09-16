package com.memento.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A small hanko-style seal: a rounded square stamped with kanji.
 *
 * Purely decorative — used to warm empty states into a journal feel. Colours are parameterised so
 * surfaces with their own palette (e.g. the achievement board) can override them.
 */
@Composable
fun HankoSeal(
    text: String = "思い出",
    size: Dp = 46.dp,
    containerColor: Color = MaterialTheme.colorScheme.secondary,
    contentColor: Color = MaterialTheme.colorScheme.onSecondary,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 4))
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(4.dp),
        )
    }
}
