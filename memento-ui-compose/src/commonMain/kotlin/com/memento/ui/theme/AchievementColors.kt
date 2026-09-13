package com.memento.ui.theme

import androidx.compose.ui.graphics.Color
import com.memento.domain.collection.AchievementTier

/**
 * World-of-Warcraft-inspired achievement palette: warm gold on near-black parchment, with
 * bronze/silver/gold tiers. Kept independent of [MementoTheme] so the achievement panel keeps its
 * game-like look in either light or dark app themes.
 */
object AchievementColors {
    val Gold = Color(0xFFFFD100)
    val GoldDark = Color(0xFFB8860B)
    val Bronze = Color(0xFFCD7F32)
    val Silver = Color(0xFFBFC3C7)
    val Panel = Color(0xFF1C1813)
    val PanelRaised = Color(0xFF262019)
    val LockedGrey = Color(0xFF6B6B6B)
    val AchievementBlue = Color(0xFF4FA3E3)
    val EarnedText = Color(0xFF9BE38B)
}

/** Primary accent for a tier's medal/shield and progress fill. */
fun tierColor(tier: AchievementTier): Color = when (tier) {
    AchievementTier.BRONZE -> AchievementColors.Bronze
    AchievementTier.SILVER -> AchievementColors.Silver
    AchievementTier.GOLD -> AchievementColors.Gold
}

/** Dim, translucent container tint hinting at a tier behind a dark achievement card. */
fun tierContainer(tier: AchievementTier): Color = tierColor(tier).copy(alpha = 0.14f)
