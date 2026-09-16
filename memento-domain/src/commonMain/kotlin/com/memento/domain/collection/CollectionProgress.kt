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
 * True when a memento references this item in isolation, i.e. its [ChecklistItem.label] or
 * [ChecklistItem.japaneseLabel] appears (case-insensitively, after trimming) in the memento's
 * `title` or `tastingNotes.text`, or one of its identifiers ([ChecklistItem.id],
 * [ChecklistItem.label], [ChecklistItem.japaneseLabel] or [ChecklistItem.matchTags]) equals one of
 * the memento's tags.
 *
 * This is a *raw*, single-item building block: it does not know about sibling items, so prefer
 * [Collection.completedItemIds] for progress — that resolves overlaps between items whose labels
 * nest (e.g. "Ayataka" vs "Hot Ayataka"). Coverage deliberately ignores [ChecklistItem.brand], so
 * recording one drink from a brand never unlocks every item of it. Pure; does not mutate its args.
 */
internal fun ChecklistItem.isCoveredBy(memento: Memento): Boolean =
    matchesTagExactly(memento) || matchedTextNeedles(memento).isNotEmpty()

/** True when an id/label/japanese-label/match-tag of this item equals one of the memento's tags. */
private fun ChecklistItem.matchesTagExactly(memento: Memento): Boolean {
    val identifiers = buildList {
        add(id)
        add(label.trim())
        japaneseLabel?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
        addAll(matchTags)
    }
    return identifiers.any { identifier ->
        memento.tags.any { it.value.equals(identifier, ignoreCase = true) }
    }
}

/** The trimmed label / Japanese label needles of this item that literally occur in [memento]. */
private fun ChecklistItem.matchedTextNeedles(memento: Memento): List<String> = buildList {
    val itemLabel = label.trim()
    if (itemLabel.isNotEmpty() && memento.containsText(itemLabel)) add(itemLabel)
    val jp = japaneseLabel?.trim()
    if (!jp.isNullOrEmpty() && memento.containsText(jp)) add(jp)
}

private fun Memento.containsText(needle: String): Boolean {
    if (title.contains(needle, ignoreCase = true)) return true
    return tastingNotes?.text?.contains(needle, ignoreCase = true) == true
}

/**
 * The ids covered by a single [memento], resolving overlaps between items whose labels nest.
 *
 * A text match on a shorter label is discarded only when *every* occurrence of it sits inside an
 * occurrence of a longer sibling label. So "Hot Ayataka" covers only `hot-ayataka`, while
 * "Ayataka and Hot Ayataka" covers both, because the standalone "Ayataka" is not inside the
 * "Hot Ayataka" span. Explicit tag matches are always honoured.
 */
internal fun Collection.coveredItemIdsBy(memento: Memento): Set<String> {
    val texts = buildList {
        add(memento.title)
        memento.tastingNotes?.text?.let { add(it) }
    }
    val exactMatches = items.filter { it.matchesTagExactly(memento) }
    val textMatches = items.mapNotNull { item ->
        item.matchedTextNeedles(memento).takeIf { it.isNotEmpty() }?.let { item to it }
    }
    val mostSpecific = textMatches.filter { (item, needles) ->
        textMatches.none { (other, otherNeedles) ->
            other.id != item.id && otherNeedles.any { longer ->
                needles.any { shorter ->
                    longer.length > shorter.length && isFullyContained(shorter, longer, texts)
                }
            }
        }
    }
    return buildSet {
        exactMatches.forEach { add(it.id) }
        mostSpecific.forEach { (item, _) -> add(item.id) }
    }
}

/**
 * True when every occurrence of [shorter] in [texts] lies within an occurrence of [longer], i.e.
 * the shorter label is only ever mentioned as part of the longer one. Case-insensitive.
 */
private fun isFullyContained(shorter: String, longer: String, texts: List<String>): Boolean {
    var sawOccurrence = false
    for (text in texts) {
        val longerSpans = buildList {
            var start = text.indexOf(longer, ignoreCase = true)
            while (start >= 0) {
                add(start..(start + longer.length - 1))
                start = text.indexOf(longer, start + 1, ignoreCase = true)
            }
        }
        var index = text.indexOf(shorter, ignoreCase = true)
        while (index >= 0) {
            sawOccurrence = true
            val end = index + shorter.length - 1
            if (longerSpans.none { index >= it.first && end <= it.last }) return false
            index = text.indexOf(shorter, index + 1, ignoreCase = true)
        }
    }
    return sawOccurrence
}

fun Collection.completedItemIds(mementos: List<Memento>): Set<String> =
    mementos.flatMap { coveredItemIdsBy(it) }.toSet()

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
