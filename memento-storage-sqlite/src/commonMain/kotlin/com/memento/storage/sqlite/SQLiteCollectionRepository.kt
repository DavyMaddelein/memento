package com.memento.storage.sqlite

import app.cash.sqldelight.db.SqlDriver
import com.memento.domain.model.ChecklistItem
import com.memento.domain.model.Collection
import com.memento.domain.model.CollectionCategory
import com.memento.domain.model.CollectionId
import com.memento.storage.contract.CollectionRepository
import com.memento.storage.sqlite.db.MementoDatabase
import com.memento.storage.sqlite.db.MementoQueries
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

/**
 * SQLite-backed [CollectionRepository]. Collections own their categories, checklist items and
 * item match-tags; saving a collection replaces the full child graph atomically.
 */
class SQLiteCollectionRepository(driver: SqlDriver) : CollectionRepository {
    private val database = MementoDatabase(driver)
    private val queries: MementoQueries = database.mementoQueries
    private val version = MutableStateFlow(0L)

    override fun observeCollections(): Flow<List<Collection>> = version.map { loadAll() }

    override fun observeCollection(id: CollectionId): Flow<Collection?> =
        version.map { loadOne(id) }

    override suspend fun getCollection(id: CollectionId): Collection? = loadOne(id)

    override suspend fun saveCollection(collection: Collection): Result<Unit> = runCatching {
        database.transaction {
            queries.upsertCollection(
                id = collection.id.value,
                name = collection.name,
                description = collection.description,
                created_at = collection.createdAt.toString(),
                meta_achievement_name = collection.metaAchievementName,
                meta_achievement_description = collection.metaAchievementDescription,
            )

            deleteChildren(collection.id.value)

            collection.categories.forEachIndexed { index, category ->
                queries.insertCategory(
                    id = category.id,
                    collection_id = collection.id.value,
                    name = category.name,
                    position = index.toLong(),
                    bonus_points = category.bonusPoints.toLong(),
                )
            }

            collection.items.forEachIndexed { index, item ->
                queries.insertChecklistItem(
                    id = item.id,
                    collection_id = collection.id.value,
                    label = item.label,
                    category_id = item.categoryId,
                    brand = item.brand,
                    position = index.toLong(),
                    points = item.points.toLong(),
                )
                item.matchTags.forEachIndexed { tagIndex, tag ->
                    queries.insertItemTag(
                        collection_id = collection.id.value,
                        checklist_item_id = item.id,
                        tag = tag,
                        position = tagIndex.toLong(),
                    )
                }
            }
        }
    }.onSuccess { version.value += 1 }

    override suspend fun deleteCollection(id: CollectionId): Result<Unit> = runCatching {
        database.transaction {
            deleteChildren(id.value)
            queries.deleteCollection(id.value)
        }
    }.onSuccess { version.value += 1 }

    private fun deleteChildren(collectionId: String) {
        queries.deleteItemTagsForCollection(collectionId)
        queries.deleteItemsForCollection(collectionId)
        queries.deleteCategoriesForCollection(collectionId)
    }

    private fun loadAll(): List<Collection> =
        queries.selectAllCollections().executeAsList().map { it.toDomain() }

    private fun loadOne(id: CollectionId): Collection? =
        queries.selectCollectionById(id.value).executeAsOneOrNull()?.toDomain()

    private fun com.memento.storage.sqlite.db.Collection.toDomain(): Collection {
        val collectionId = id
        val categories = queries.selectCategoriesForCollection(collectionId)
            .executeAsList()
            .map { CollectionCategory(it.id, it.name, it.bonus_points.toInt()) }
        val items = queries.selectItemsForCollection(collectionId).executeAsList().map { row ->
            ChecklistItem(
                id = row.id,
                label = row.label,
                categoryId = row.category_id,
                brand = row.brand,
                matchTags = queries.selectItemTags(collectionId, row.id).executeAsList(),
                points = row.points.toInt(),
            )
        }

        return Collection(
            id = CollectionId(collectionId),
            name = name,
            description = description,
            categories = categories,
            items = items,
            createdAt = Instant.parse(created_at),
            metaAchievementName = meta_achievement_name,
            metaAchievementDescription = meta_achievement_description,
        )
    }
}
