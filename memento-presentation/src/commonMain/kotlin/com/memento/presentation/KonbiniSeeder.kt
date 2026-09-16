package com.memento.presentation

import com.memento.domain.collection.KonbiniDrinkChecklist
import com.memento.domain.model.Collection
import com.memento.storage.contract.CollectionRepository
import kotlinx.datetime.Instant

/**
 * Ensures the flagship konbini collection exists and is up to date.
 *
 * A fresh install has no collection, so the canonical definition is saved. Installations created
 * before Japanese labels were persisted (schema v3) already have the collection with
 * `japaneseLabel == null`; the seed guard would otherwise never revisit them, so they are repaired
 * by re-saving the canonical definition (preserving the original creation timestamp).
 *
 * @return true when the repository was written to.
 */
suspend fun seedKonbiniCollection(
    collectionRepository: CollectionRepository,
    now: Instant,
): Boolean {
    val canonical = KonbiniDrinkChecklist.create(now)
    val existing = collectionRepository.getCollection(canonical.id)
    return when {
        existing == null -> collectionRepository.saveCollection(canonical).isSuccess
        needsJapaneseLabelBackfill(existing, canonical) ->
            collectionRepository.saveCollection(canonical.copy(createdAt = existing.createdAt)).isSuccess
        else -> false
    }
}

/** True when the canonical definition carries a Japanese label the stored one is missing. */
internal fun needsJapaneseLabelBackfill(existing: Collection, canonical: Collection): Boolean {
    val canonicalById = canonical.items.associateBy { it.id }
    return existing.items.any { item ->
        item.japaneseLabel.isNullOrBlank() && !canonicalById[item.id]?.japaneseLabel.isNullOrBlank()
    }
}
