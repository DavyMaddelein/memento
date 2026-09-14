package com.memento.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.memento.domain.collection.Achievement
import com.memento.domain.collection.AchievementKind
import com.memento.domain.collection.CollectionAchievementBoard
import com.memento.domain.model.ChecklistItem
import com.memento.domain.model.Collection
import com.memento.domain.model.Memento
import com.memento.presentation.CollectionProgressUiState
import com.memento.presentation.formatIsoDate
import com.memento.ui.components.TagChip
import com.memento.ui.theme.AchievementColors
import com.memento.ui.theme.tierColor
import com.memento.ui.theme.tierContainer

private val TextPrimary = Color(0xFFF1EAD9)
private val TextMuted = Color(0xFFA99F8C)

/**
 * Achievement-style detail view for a curated collection. Renders the collection's earned points,
 * a pinned meta achievement and one card per achievement, tier by tier. Stateless — every action is
 * surfaced as a callback so the screen stays platform-agnostic.
 */
@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionDetailScreen(
    state: CollectionProgressUiState,
    images: (Memento) -> List<ImageBitmap?> = { emptyList() },
    onBack: () -> Unit = {},
    onRecordItem: (ChecklistItem) -> Unit = {},
    onAcknowledgeEarned: () -> Unit = {},
    onToggleHideCompleted: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = AchievementColors.Panel,
        topBar = {
            TopAppBar(
                title = { Text("Achievements") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AchievementColors.Panel,
                    titleContentColor = AchievementColors.Gold,
                    navigationIconContentColor = AchievementColors.Gold,
                ),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val collection = state.collection
            when {
                state.isLoading && collection == null -> {
                    CircularProgressIndicator(
                        color = AchievementColors.Gold,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                collection == null -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "No collection yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                        )
                        Text(
                            text = "Record a keepsake to start earning achievements.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted,
                        )
                    }
                }

                else -> {
                    AchievementBody(
                        collection = collection,
                        state = state,
                        onRecordItem = onRecordItem,
                        onToggleHideCompleted = onToggleHideCompleted,
                    )
                }
            }

            CelebrationOverlay(
                achievements = state.recentlyEarned,
                onDismiss = onAcknowledgeEarned,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

private data class AchievementSection(
    val id: String,
    val name: String,
    val category: Achievement?,
    val items: List<Achievement>,
)

@Composable
private fun AchievementBody(
    collection: Collection,
    state: CollectionProgressUiState,
    onRecordItem: (ChecklistItem) -> Unit,
    onToggleHideCompleted: () -> Unit,
) {
    val board = state.board
    val progress = state.progress
    val earnedPoints = board?.earnedPoints ?: progress?.completedItems ?: 0
    val totalPoints = board?.totalPoints ?: progress?.totalItems ?: 0
    val earnedCount = board?.earnedCount ?: 0
    val totalCount = board?.totalCount ?: collection.items.size
    val fraction = if (totalPoints == 0) 0f else earnedPoints.toFloat() / totalPoints.toFloat()
    val percent = (fraction * 100f).toInt()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AchievementHero(
            name = collection.name,
            description = collection.description,
            earnedPoints = earnedPoints,
            totalPoints = totalPoints,
            earnedCount = earnedCount,
            totalCount = totalCount,
            fraction = fraction,
            percent = percent,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TagChip(
                label = if (state.hideCompleted) "Hiding completed" else "Show uncompleted only",
                selected = state.hideCompleted,
                onClick = onToggleHideCompleted,
            )
        }

        if (board != null) {
            board.meta?.let { meta ->
                MetaAchievementCard(
                    achievement = meta,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            AchievementSections(
                sections = buildSections(collection, board),
                collection = collection,
                hideCompleted = state.hideCompleted,
                onRecordItem = onRecordItem,
            )
        } else {
            Text(
                text = "Achievements are unavailable for this collection.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun AchievementHero(
    name: String,
    description: String,
    earnedPoints: Int,
    totalPoints: Int,
    earnedCount: Int,
    totalCount: Int,
    fraction: Float,
    percent: Int,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AchievementColors.Panel)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
        )
        if (description.isNotBlank()) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = earnedPoints.toString(),
                style = MaterialTheme.typography.displayMedium,
                color = AchievementColors.Gold,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "/ $totalPoints points",
                style = MaterialTheme.typography.bodyMedium,
                color = AchievementColors.GoldDark,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Text(
            text = "$earnedCount of $totalCount achievements earned",
            style = MaterialTheme.typography.labelLarge,
            color = TextMuted,
        )
        ThickProgressBar(
            fraction = fraction,
            height = 12.dp,
            trackColor = AchievementColors.PanelRaised,
            fillColor = AchievementColors.Gold,
        )
        Text(
            text = "$percent%",
            style = MaterialTheme.typography.labelMedium,
            color = AchievementColors.Gold,
        )
    }
}

@Composable
private fun MetaAchievementCard(
    achievement: Achievement,
    modifier: Modifier = Modifier,
) {
    val earned = achievement.earned
    val shape = RoundedCornerShape(12.dp)
    val borderColor = if (earned) AchievementColors.Gold else AchievementColors.LockedGrey.copy(alpha = 0.55f)
    val borderWidth = if (earned) 2.dp else 1.dp
    val container = if (earned) {
        AchievementColors.GoldDark.copy(alpha = 0.20f)
    } else {
        AchievementColors.PanelRaised
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(container)
            .then(
                if (earned) {
                    Modifier.border(6.dp, AchievementColors.Gold.copy(alpha = 0.12f), shape)
                } else {
                    Modifier
                },
            )
            .border(borderWidth, borderColor, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                imageVector = Icons.Filled.MilitaryTech,
                contentDescription = null,
                tint = if (earned) AchievementColors.Gold else AchievementColors.LockedGrey,
                modifier = Modifier.size(34.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = achievement.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = if (earned) AchievementColors.Gold else TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    PointsBadge(points = achievement.points, earned = earned)
                }
                Text(
                    text = achievement.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                )
            }
        }
        if (earned) {
            achievement.earnedAt?.let { earnedAt ->
                Text(
                    text = "Earned ${formatIsoDate(earnedAt)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = AchievementColors.EarnedText,
                )
            }
        } else {
            Text(
                text = "categories: ${achievement.progress}/${achievement.target}",
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted,
            )
            ThickProgressBar(
                fraction = achievement.fraction,
                height = 6.dp,
                trackColor = AchievementColors.Panel,
                fillColor = AchievementColors.GoldDark,
            )
        }
    }
}

@Composable
private fun AchievementSections(
    sections: List<AchievementSection>,
    collection: Collection,
    hideCompleted: Boolean = false,
    onRecordItem: (ChecklistItem) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        sections.forEach { section ->
            val visibleItems = if (hideCompleted) section.items.filter { !it.earned } else section.items
            if (visibleItems.isNotEmpty() || !hideCompleted) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SectionHeader(section)
                    visibleItems.forEach { achievement ->
                        AchievementRow(
                            achievement = achievement,
                            checklistItem = achievement.itemId?.let { itemId ->
                                collection.items.firstOrNull { it.id == itemId }
                            },
                            onRecordItem = onRecordItem,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(section: AchievementSection) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = section.name,
            style = MaterialTheme.typography.titleMedium,
            color = AchievementColors.Silver,
            modifier = Modifier.weight(1f),
        )
        section.category?.let { category ->
            Text(
                text = "${category.progress}/${category.target}",
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted,
            )
            Text(
                text = "${category.points} pts",
                style = MaterialTheme.typography.labelMedium,
                color = if (category.earned) AchievementColors.Gold else AchievementColors.GoldDark,
            )
        }
    }
}

@Composable
private fun AchievementRow(
    achievement: Achievement,
    checklistItem: ChecklistItem?,
    onRecordItem: (ChecklistItem) -> Unit,
) {
    val earned = achievement.earned
    val accent = tierColor(achievement.tier)
    val shape = RoundedCornerShape(10.dp)
    val container = if (earned) {
        AchievementColors.PanelRaised
    } else {
        AchievementColors.PanelRaised.copy(alpha = 0.6f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(shape)
            .background(container)
            .then(
                if (earned) {
                    Modifier.border(1.dp, AchievementColors.Gold.copy(alpha = 0.35f), shape)
                } else {
                    Modifier
                },
            ),
    ) {
        if (earned) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(4.dp)
                    .background(AchievementColors.Gold),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(38.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (earned) tierContainer(achievement.tier) else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.MilitaryTech,
                    contentDescription = null,
                    tint = if (earned) accent else AchievementColors.LockedGrey,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = achievement.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (earned) TextPrimary else AchievementColors.LockedGrey,
                    fontWeight = if (earned) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val jpLabel = checklistItem?.japaneseLabel
                if (!jpLabel.isNullOrBlank()) {
                    Text(
                        text = jpLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (earned) AchievementColors.Gold else AchievementColors.Silver,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = achievement.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (achievement.target > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ThickProgressBar(
                            fraction = achievement.fraction,
                            height = 5.dp,
                            trackColor = AchievementColors.Panel,
                            fillColor = if (earned) accent else AchievementColors.GoldDark,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${achievement.progress}/${achievement.target}",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextMuted,
                        )
                    }
                }
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                PointsBadge(points = achievement.points, earned = earned)
                if (earned) {
                    achievement.earnedAt?.let { earnedAt ->
                        Text(
                            text = "Earned ${formatIsoDate(earnedAt)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = AchievementColors.EarnedText,
                        )
                    }
                } else if (checklistItem != null) {
                    TextButton(onClick = { onRecordItem(checklistItem) }) {
                        Text("Record", color = AchievementColors.AchievementBlue)
                    }
                }
            }
        }
    }
}

@Composable
private fun PointsBadge(points: Int, earned: Boolean) {
    val shape = RoundedCornerShape(6.dp)
    val accent = if (earned) AchievementColors.Gold else AchievementColors.GoldDark
    Box(
        modifier = Modifier
            .clip(shape)
            .background(accent.copy(alpha = 0.16f))
            .border(1.dp, accent.copy(alpha = 0.6f), shape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = "+$points pts",
            style = MaterialTheme.typography.labelMedium,
            color = accent,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ThickProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    trackColor: Color = AchievementColors.PanelRaised,
    fillColor: Color = AchievementColors.Gold,
) {
    val clamped = fraction.coerceIn(0f, 1f)
    val shape = RoundedCornerShape(height / 2)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(trackColor),
    ) {
        if (clamped > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(clamped)
                    .clip(shape)
                    .background(fillColor),
            )
        }
    }
}

@Composable
private fun CelebrationOverlay(
    achievements: List<Achievement>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = achievements.isNotEmpty(),
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn() + expandVertically(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        val shape = RoundedCornerShape(14.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clip(shape)
                .background(AchievementColors.PanelRaised)
                .border(3.dp, AchievementColors.Gold, shape)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.MilitaryTech,
                    contentDescription = null,
                    tint = AchievementColors.Gold,
                    modifier = Modifier.size(30.dp),
                )
                Text(
                    text = "Achievement Earned!",
                    style = MaterialTheme.typography.titleLarge,
                    color = AchievementColors.Gold,
                    modifier = Modifier.weight(1f),
                )
            }
            achievements.forEach { achievement ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = achievement.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "+${achievement.points} points",
                        style = MaterialTheme.typography.labelLarge,
                        color = AchievementColors.Gold,
                    )
                }
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("Great!", color = AchievementColors.Gold)
            }
        }
    }
}

private fun buildSections(
    collection: Collection,
    board: CollectionAchievementBoard,
): List<AchievementSection> {
    val nonMeta = board.achievements.filter { it.kind != AchievementKind.META }
    val sections = collection.categories
        .map { category ->
            AchievementSection(
                id = category.id,
                name = category.name,
                category = nonMeta.firstOrNull {
                    it.kind == AchievementKind.CATEGORY && it.categoryId == category.id
                },
                items = nonMeta.filter {
                    it.kind == AchievementKind.ITEM && it.categoryId == category.id
                },
            )
        }
        .filter { it.category != null || it.items.isNotEmpty() }

    val knownIds = collection.categories.map { it.id }.toSet()
    val orphans = nonMeta.filter {
        it.kind == AchievementKind.ITEM && (it.categoryId == null || it.categoryId !in knownIds)
    }
    return if (orphans.isEmpty()) {
        sections
    } else {
        sections + AchievementSection(id = "__other", name = "Other", category = null, items = orphans)
    }
}
