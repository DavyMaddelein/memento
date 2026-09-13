package com.memento.storage.sqlite

import app.cash.sqldelight.db.SqlDriver
import com.memento.domain.model.ChecklistItem
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
import com.memento.domain.model.TastingNotes
import kotlinx.datetime.Instant

val T1: Instant = Instant.parse("2024-01-01T00:00:00Z")
val T2: Instant = Instant.parse("2024-02-01T00:00:00Z")
val T3: Instant = Instant.parse("2024-03-01T00:00:00Z")

/** Fresh in-memory database sharing a single underlying connection. */
fun newInMemoryDriver(): SqlDriver =
    DatabaseDriverFactory(DatabaseDriverFactory.IN_MEMORY).createDriver()

fun fullMemento(
    id: String = "m1",
    occurredAt: Instant = T1,
): Memento = Memento(
    id = MementoId(id),
    title = "Tokyo Konbini",
    reflection = "Rainy night in Roppongi",
    coordinates = Coordinates(35.66, 139.73, 8.0),
    place = Place(
        name = "7-Eleven",
        brand = "Seven",
        neighborhood = "Roppongi",
        city = "Tokyo",
        country = "Japan",
    ),
    media = listOf(
        MediaReference(MediaId("$id-media1"), "file://a.jpg", "image/jpeg", T1),
        MediaReference(MediaId("$id-media2"), "file://b.mp4", "video/mp4", T2),
    ),
    rating = Rating(4),
    tastingNotes = TastingNotes("Strong coffee", listOf("bitter", "sweet")),
    tags = listOf(Tag("coffee"), Tag("travel")),
    collectionIds = listOf(CollectionId("c1"), CollectionId("c2")),
    priceMinorUnits = 250,
    currencyCode = "JPY",
    occurredAt = occurredAt,
    createdAt = T1,
    updatedAt = T2,
)

fun minimalMemento(
    id: String = "m-min",
    occurredAt: Instant = T1,
): Memento = Memento(
    id = MementoId(id),
    title = "Minimal",
    occurredAt = occurredAt,
    createdAt = occurredAt,
    updatedAt = occurredAt,
)

fun fullCollection(id: String = "c1"): Collection = Collection(
    id = CollectionId(id),
    name = "Japan Konbini Drinks 2026",
    description = "Trying every drink",
    categories = listOf(
        CollectionCategory("cat1", "Coffee"),
        CollectionCategory("cat2", "Tea"),
    ),
    items = listOf(
        ChecklistItem(
            id = "item1",
            label = "Boss Coffee Rainbow Mountain",
            categoryId = "cat1",
            brand = "Suntory",
            matchTags = listOf("boss", "rainbow"),
        ),
        ChecklistItem(
            id = "item2",
            label = "Oi Ocha",
            categoryId = "cat2",
            brand = "Ito En",
        ),
    ),
    createdAt = T1,
)
