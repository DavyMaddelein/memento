package com.memento.storage.sqlite

import app.cash.sqldelight.db.SqlDriver
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
import com.memento.storage.contract.MementoRepository
import com.memento.storage.sqlite.db.MementoDatabase
import com.memento.storage.sqlite.db.MementoQueries
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

/**
 * SQLite-backed [MementoRepository].
 *
 * Aggregates are stored across [MementoDatabase] relational tables. Every successful mutation
 * bumps an internal version counter so that `observe*` flows re-read the affected rows.
 */
class SQLiteMementoRepository(driver: SqlDriver) : MementoRepository {
    private val database = MementoDatabase(driver)
    private val queries: MementoQueries = database.mementoQueries
    private val version = MutableStateFlow(0L)

    override fun observeAllMementos(): Flow<List<Memento>> = version.map { loadAll() }

    override fun observeMemento(id: MementoId): Flow<Memento?> = version.map { loadOne(id) }

    override suspend fun getMemento(id: MementoId): Memento? = loadOne(id)

    override suspend fun saveMemento(memento: Memento): Result<Unit> = runCatching {
        database.transaction {
            upsertGraph(memento)
        }
    }.onSuccess { version.value += 1 }

    override suspend fun saveMementos(mementos: List<Memento>): Result<Unit> = runCatching {
        database.transaction {
            mementos.forEach { upsertGraph(it) }
        }
    }.onSuccess { version.value += 1 }

    override suspend fun deleteMemento(id: MementoId): Result<Unit> = runCatching {
        database.transaction {
            deleteChildren(id.value)
            queries.deleteMemento(id.value)
        }
    }.onSuccess { version.value += 1 }

    override suspend fun deleteAllMementos(): Result<Unit> = runCatching {
        database.transaction {
            queries.selectAllMementos().executeAsList().forEach { deleteChildren(it.id) }
            queries.deleteAllMementos()
        }
    }.onSuccess { version.value += 1 }

    private fun upsertGraph(memento: Memento) {
        queries.upsertMemento(
            id = memento.id.value,
            title = memento.title,
            reflection = memento.reflection,
            latitude = memento.coordinates?.latitude,
            longitude = memento.coordinates?.longitude,
            accuracy_meters = memento.coordinates?.accuracyMeters,
            place_name = memento.place?.name,
            place_brand = memento.place?.brand,
            place_neighborhood = memento.place?.neighborhood,
            place_city = memento.place?.city,
            place_country = memento.place?.country,
            rating = memento.rating?.stars?.toLong(),
            tasting_text = memento.tastingNotes?.text,
            price_minor_units = memento.priceMinorUnits,
            currency_code = memento.currencyCode,
            occurred_at = memento.occurredAt.toString(),
            created_at = memento.createdAt.toString(),
            updated_at = memento.updatedAt.toString(),
        )

        deleteChildren(memento.id.value)

        memento.tags.forEachIndexed { index, tag ->
            queries.insertMementoTag(
                memento_id = memento.id.value,
                tag = tag.value,
                kind = TAG_KIND,
                position = index.toLong(),
            )
        }
        memento.tastingNotes?.flavorTags?.forEachIndexed { index, flavor ->
            queries.insertMementoTag(
                memento_id = memento.id.value,
                tag = flavor,
                kind = FLAVOR_KIND,
                position = index.toLong(),
            )
        }
        memento.collectionIds.forEachIndexed { index, collectionId ->
            queries.insertMementoCollection(
                memento_id = memento.id.value,
                collection_id = collectionId.value,
                position = index.toLong(),
            )
        }
        memento.media.forEachIndexed { index, media ->
            queries.upsertMediaRef(
                id = media.id.value,
                memento_id = memento.id.value,
                uri = media.uri,
                mime_type = media.mimeType,
                captured_at = media.capturedAt.toString(),
                position = index.toLong(),
            )
        }
    }

    private fun deleteChildren(mementoId: String) {
        queries.deleteTagsForMemento(mementoId)
        queries.deleteCollectionsForMemento(mementoId)
        queries.deleteMediaForMemento(mementoId)
    }

    private fun loadAll(): List<Memento> =
        queries.selectAllMementos().executeAsList().map { it.toDomain() }

    private fun loadOne(id: MementoId): Memento? =
        queries.selectMementoById(id.value).executeAsOneOrNull()?.toDomain()

    private fun com.memento.storage.sqlite.db.Memento.toDomain(): Memento {
        val mementoId = id
        val tags = queries.selectTagsForMemento(mementoId).executeAsList().map { Tag(it) }
        val flavorTags = queries.selectFlavorTagsForMemento(mementoId).executeAsList()
        val collectionIds = queries.selectCollectionIdsForMemento(mementoId)
            .executeAsList()
            .map { CollectionId(it) }
        val media = queries.selectMediaForMemento(mementoId).executeAsList().map {
            MediaReference(
                id = MediaId(it.id),
                uri = it.uri,
                mimeType = it.mime_type,
                capturedAt = Instant.parse(it.captured_at),
            )
        }

        val coordinates = if (latitude != null && longitude != null) {
            Coordinates(latitude, longitude, accuracy_meters)
        } else {
            null
        }

        val place = place_name?.let {
            Place(
                name = it,
                brand = place_brand,
                neighborhood = place_neighborhood,
                city = place_city,
                country = place_country,
            )
        }

        val rating = rating?.toInt()?.let { Rating(it) }

        val tastingNotes = if (tasting_text != null || flavorTags.isNotEmpty()) {
            TastingNotes(text = tasting_text ?: "", flavorTags = flavorTags)
        } else {
            null
        }

        return Memento(
            id = MementoId(mementoId),
            title = title,
            reflection = reflection,
            coordinates = coordinates,
            place = place,
            media = media,
            rating = rating,
            tastingNotes = tastingNotes,
            tags = tags,
            collectionIds = collectionIds,
            priceMinorUnits = price_minor_units,
            currencyCode = currency_code,
            occurredAt = Instant.parse(occurred_at),
            createdAt = Instant.parse(created_at),
            updatedAt = Instant.parse(updated_at),
        )
    }

    private companion object {
        const val TAG_KIND: String = "TAG"
        const val FLAVOR_KIND: String = "FLAVOR"
    }
}
