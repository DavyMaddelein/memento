package com.memento.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Aggregate root for a single recorded keepsake moment.
 *
 * Cross-field rules live in [com.memento.domain.validation.MementoValidator]; the value objects
 * referenced here already enforce their own invariants at construction time.
 */
@Serializable
data class Memento(
    val id: MementoId,
    val title: String = "",
    val reflection: String = "",
    val coordinates: Coordinates? = null,
    val place: Place? = null,
    val media: List<MediaReference> = emptyList(),
    val rating: Rating? = null,
    val tastingNotes: TastingNotes? = null,
    val tags: List<Tag> = emptyList(),
    val collectionIds: List<CollectionId> = emptyList(),
    val priceMinorUnits: Long? = null,
    val currencyCode: String? = null,
    val occurredAt: Instant,
    val createdAt: Instant,
    val updatedAt: Instant,
)
