package com.memento.domain.collection

import com.memento.domain.model.ChecklistItem
import com.memento.domain.model.Collection
import com.memento.domain.model.CollectionCategory
import com.memento.domain.model.CollectionId
import com.memento.domain.model.Memento

/** Progress for a single checklist category. */
data class CategoryProgress(
    val categoryId: String,
    val name: String,
    val total: Int,
    val completed: Int,
) {
    val percentage: Int
        get() = if (total == 0) 0 else (completed * 100) / total
}

/** Aggregate progress for a collection. */
data class CollectionProgress(
    val collectionId: CollectionId,
    val totalItems: Int,
    val completedItems: Int,
    val completedItemIds: Set<String>,
    val categories: List<CategoryProgress>,
) {
    val percentage: Int
        get() = if (totalItems == 0) 0 else (completedItems * 100) / totalItems
}

/**
 * True when a memento counts towards this checklist item.
 *
 * Coverage requires a strong, product-level match, so a matching brand alone is deliberately
 * not sufficient: recording one drink from a brand must not unlock every item from that brand.
 * The item is covered when either
 *  - its [ChecklistItem.label] appears (case-insensitively, after trimming) in the memento's
 *    `title` or `tastingNotes.text`, or
 *  - one of its [ChecklistItem.matchTags] equals (case-insensitively) one of the memento's tags.
 *
 * This function is pure and does not mutate its arguments.
 */
fun ChecklistItem.isCoveredBy(memento: Memento): Boolean {
    val itemLabel = label.trim()
    if (memento.title.contains(itemLabel, ignoreCase = true)) return true
    if (memento.tastingNotes?.text?.contains(itemLabel, ignoreCase = true) == true) return true
    return matchTags.any { tag -> memento.tags.any { it.value.equals(tag, ignoreCase = true) } }
}

fun Collection.completedItemIds(mementos: List<Memento>): Set<String> =
    items.filter { item -> mementos.any { item.isCoveredBy(it) } }.map { it.id }.toSet()

fun Collection.calculateCompletionPercentage(mementos: List<Memento>): Int {
    if (items.isEmpty()) return 0
    return (completedItemIds(mementos).size * 100) / items.size
}

fun Collection.getCoveredCategories(mementos: List<Memento>): List<CollectionCategory> {
    val coveredIds = completedItemIds(mementos)
    val coveredCategoryIds = items
        .filter { it.id in coveredIds }
        .mapNotNull { it.categoryId }
        .toSet()
    return categories.filter { it.id in coveredCategoryIds }
}

fun Collection.getCoveredBrands(mementos: List<Memento>): Set<String> =
    mementos.mapNotNull { it.place?.brand }.toSet()

fun Collection.progress(mementos: List<Memento>): CollectionProgress {
    val completed = completedItemIds(mementos)
    val categoryProgress = categories.map { category ->
        val categoryItems = items.filter { it.categoryId == category.id }
        CategoryProgress(
            categoryId = category.id,
            name = category.name,
            total = categoryItems.size,
            completed = categoryItems.count { it.id in completed },
        )
    }
    return CollectionProgress(
        collectionId = id,
        totalItems = items.size,
        completedItems = completed.size,
        completedItemIds = completed,
        categories = categoryProgress,
    )
}
