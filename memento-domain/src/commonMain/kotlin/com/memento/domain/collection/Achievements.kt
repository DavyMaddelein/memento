package com.memento.domain.collection

import com.memento.domain.model.ChecklistItem
import com.memento.domain.model.Collection
import com.memento.domain.model.Memento
import kotlinx.datetime.Instant

/** Visual tier of an achievement, from common bronze to prestigious gold. */
enum class AchievementTier { BRONZE, SILVER, GOLD }

/** What a piece of progress is tied to. */
enum class AchievementKind { ITEM, CATEGORY, META }

/**
 * A single unlockable achievement derived from a collection and the mementos that cover it.
 *
 * [progress] and [target] describe how far the player has come; when [target] is zero [fraction]
 * is defined as `0f` so callers never divide by zero.
 */
data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val points: Int,
    val tier: AchievementTier,
    val kind: AchievementKind,
    val earned: Boolean,
    val earnedAt: Instant?,
    val progress: Int,
    val target: Int,
    val categoryId: String? = null,
    val itemId: String? = null,
) {
    val isMeta: Boolean get() = kind == AchievementKind.META

    val fraction: Float
        get() = if (target == 0) 0f else progress.toFloat() / target.toFloat()
}

/** The full achievement board for a collection. */
data class CollectionAchievementBoard(
    val achievements: List<Achievement>,
    val meta: Achievement?,
    val totalPoints: Int,
    val earnedPoints: Int,
    val earnedCount: Int,
    val totalCount: Int,
) {
    val percentage: Int
        get() = if (totalPoints == 0) 0 else (earnedPoints * 100) / totalPoints

    val isComplete: Boolean
        get() = totalCount > 0 && earnedCount == totalCount
}

/**
 * Derives a deterministic achievement board for this collection from [mementos].
 *
 * Every checklist item yields a bronze ITEM achievement, every category a silver CATEGORY
 * achievement, and collections with categories additionally yield a gold META achievement.
 * Inputs are never mutated.
 */
fun Collection.achievementBoard(mementos: List<Memento>): CollectionAchievementBoard {
    val itemAchievements = items.map { item ->
        val covering = mementos.filter { item.isCoveredBy(it) }
        val covered = covering.isNotEmpty()
        Achievement(
            id = "item:" + item.id,
            title = item.label,
            description = itemDescription(item),
            points = item.points,
            tier = AchievementTier.BRONZE,
            kind = AchievementKind.ITEM,
            earned = covered,
            earnedAt = if (covered) covering.minOf { it.occurredAt } else null,
            progress = if (covered) 1 else 0,
            target = 1,
            categoryId = item.categoryId,
            itemId = item.id,
        )
    }
    val itemsByCategory = items.groupBy { it.categoryId }

    val categoryAchievements = categories.map { category ->
        val categoryItems = itemsByCategory[category.id].orEmpty()
        val earnedItems = itemAchievements.filter { it.categoryId == category.id && it.earned }
        val target = categoryItems.size
        val progress = earnedItems.size
        val earned = target > 0 && progress == target
        Achievement(
            id = "category:" + category.id,
            title = category.name,
            description = "Complete every ${category.name} entry",
            points = category.bonusPoints,
            tier = AchievementTier.SILVER,
            kind = AchievementKind.CATEGORY,
            earned = earned,
            earnedAt = if (earned) earnedItems.mapNotNull { it.earnedAt }.maxOrNull() else null,
            progress = progress,
            target = target,
            categoryId = category.id,
        )
    }

    val metaAchievement = if (categories.isNotEmpty()) {
        val earnedCategories = categoryAchievements.filter { it.earned }
        val earned = earnedCategories.size == categories.size
        Achievement(
            id = "meta:" + id.value,
            title = metaAchievementName ?: (name + " — Conqueror"),
            description = metaAchievementDescription
                ?: "Earn every category achievement in this collection",
            points = categories.sumOf { it.bonusPoints } + 100,
            tier = AchievementTier.GOLD,
            kind = AchievementKind.META,
            earned = earned,
            earnedAt = if (earned) earnedCategories.mapNotNull { it.earnedAt }.maxOrNull() else null,
            progress = earnedCategories.size,
            target = categories.size,
        )
    } else {
        null
    }

    val orderedItemAchievements = buildList {
        categories.forEach { category ->
            itemAchievements
                .filter { it.categoryId == category.id }
                .forEach { add(it) }
        }
        itemAchievements
            .filter { achievement -> categories.none { it.id == achievement.categoryId } }
            .forEach { add(it) }
    }

    val achievements = buildList {
        addAll(categoryAchievements)
        addAll(orderedItemAchievements)
        if (metaAchievement != null) add(metaAchievement)
    }

    val earnedAchievements = achievements.filter { it.earned }
    return CollectionAchievementBoard(
        achievements = achievements,
        meta = metaAchievement,
        totalPoints = achievements.sumOf { it.points },
        earnedPoints = earnedAchievements.sumOf { it.points },
        earnedCount = earnedAchievements.size,
        totalCount = achievements.size,
    )
}

private fun itemDescription(item: ChecklistItem): String =
    if (item.brand.isNullOrBlank()) {
        "Taste ${item.label}"
    } else {
        "Taste ${item.label} (${item.brand})"
    }
