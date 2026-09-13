package com.memento.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class CollectionCategory(
    val id: String,
    val name: String,
    val bonusPoints: Int = 25,
) {
    init {
        require(id.isNotBlank()) { "CollectionCategory id must not be blank" }
        require(name.isNotBlank()) { "CollectionCategory name must not be blank" }
    }
}

/**
 * One line on a keepsake checklist (e.g. "Boss Coffee Rainbow Mountain").
 *
 * A memento "covers" an item when the brand matches, or any of [matchTags] is present on the
 * memento, or the [label] appears in the memento title / tasting notes.
 */
@Serializable
data class ChecklistItem(
    val id: String,
    val label: String,
    val categoryId: String? = null,
    val brand: String? = null,
    val matchTags: List<String> = emptyList(),
    val points: Int = 10,
) {
    init {
        require(id.isNotBlank()) { "ChecklistItem id must not be blank" }
        require(label.isNotBlank()) { "ChecklistItem label must not be blank" }
    }
}

/**
 * A curated experiential journey such as "Japan Konbini Drinks 2026".
 */
@Serializable
data class Collection(
    val id: CollectionId,
    val name: String,
    val description: String = "",
    val categories: List<CollectionCategory> = emptyList(),
    val items: List<ChecklistItem> = emptyList(),
    val createdAt: Instant,
    val metaAchievementName: String? = null,
    val metaAchievementDescription: String? = null,
) {
    init {
        require(name.isNotBlank()) { "Collection name must not be blank" }
    }
}
