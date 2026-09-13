package com.memento.domain

import com.memento.domain.model.CollectionId
import com.memento.domain.model.Coordinates
import com.memento.domain.model.MediaId
import com.memento.domain.model.MediaReference
import com.memento.domain.model.Memento
import com.memento.domain.model.MementoId
import com.memento.domain.model.Place
import com.memento.domain.model.Rating
import com.memento.domain.model.Tag
import com.memento.domain.model.TastingNotes
import kotlinx.datetime.Instant

/** Fixed, ordered timestamps shared by the suite. */
val T1: Instant = Instant.parse("2024-01-01T00:00:00Z")
val T2: Instant = Instant.parse("2024-02-01T00:00:00Z")
val T3: Instant = Instant.parse("2024-03-01T00:00:00Z")
val T4: Instant = Instant.parse("2024-04-01T00:00:00Z")

fun mediaReference(
    id: String = "media-1",
    uri: String = "file:///photo.jpg",
    mimeType: String = "image/jpeg",
    capturedAt: Instant = T1,
): MediaReference = MediaReference(MediaId(id), uri, mimeType, capturedAt)

fun memento(
    id: String = "memento-1",
    title: String = "A keepsake",
    reflection: String = "",
    coordinates: Coordinates? = null,
    place: Place? = null,
    media: List<MediaReference> = listOf(mediaReference()),
    rating: Rating? = null,
    tastingNotes: TastingNotes? = null,
    tags: List<Tag> = emptyList(),
    collectionIds: List<CollectionId> = emptyList(),
    priceMinorUnits: Long? = null,
    currencyCode: String? = null,
    occurredAt: Instant = T1,
    createdAt: Instant = T1,
    updatedAt: Instant = T1,
): Memento = Memento(
    id = MementoId(id),
    title = title,
    reflection = reflection,
    coordinates = coordinates,
    place = place,
    media = media,
    rating = rating,
    tastingNotes = tastingNotes,
    tags = tags,
    collectionIds = collectionIds,
    priceMinorUnits = priceMinorUnits,
    currencyCode = currencyCode,
    occurredAt = occurredAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
