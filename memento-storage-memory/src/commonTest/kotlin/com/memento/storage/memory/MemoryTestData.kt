package com.memento.storage.memory

import com.memento.domain.model.Collection
import com.memento.domain.model.CollectionCategory
import com.memento.domain.model.CollectionId
import com.memento.domain.model.Coordinates
import com.memento.domain.model.MediaId
import com.memento.domain.model.MediaReference
import com.memento.domain.model.Memento
import com.memento.domain.model.MementoId
import com.memento.domain.model.Place
import com.memento.domain.model.Rating
import com.memento.domain.model.Tag
import kotlinx.datetime.Instant

val T1: Instant = Instant.parse("2024-01-01T00:00:00Z")
val T2: Instant = Instant.parse("2024-02-01T00:00:00Z")
val T3: Instant = Instant.parse("2024-03-01T00:00:00Z")
val T4: Instant = Instant.parse("2024-04-01T00:00:00Z")

fun memoryMemento(
    id: String = "memento-1",
    title: String = "A keepsake",
    reflection: String = "",
    coordinates: Coordinates? = null,
    place: Place? = null,
    media: List<MediaReference> = emptyList(),
    rating: Rating? = null,
    tags: List<Tag> = emptyList(),
    collectionIds: List<CollectionId> = emptyList(),
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
    tags = tags,
    collectionIds = collectionIds,
    occurredAt = occurredAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun memoryCollection(
    id: String = "collection-1",
    name: String = "Konbini Drinks",
    description: String = "",
    categories: List<CollectionCategory> = emptyList(),
    createdAt: Instant = T1,
): Collection = Collection(
    id = CollectionId(id),
    name = name,
    description = description,
    categories = categories,
    createdAt = createdAt,
)
