package com.memento.storage.sqlite

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.memento.domain.model.CollectionId
import com.memento.storage.sqlite.db.MementoDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MementoMigrationTest {

    @Test
    fun schemaVersionIsIncrementedByMigration() {
        assertEquals(2L, MementoDatabase.Schema.version)
    }

    @Test
    fun migrateFromV1BackfillsDefaultsAndAllowsNewFields() = runTest {
        val driver = JdbcSqliteDriver(DatabaseDriverFactory.IN_MEMORY)
        createV1CollectionTables(driver)
        driver.execute(
            null,
            "INSERT INTO collection(id, name, description, created_at) " +
                "VALUES ('c1', 'Legacy', 'v1 data', '2024-01-01T00:00:00Z')",
            0,
        )
        driver.execute(
            null,
            "INSERT INTO collection_category(id, collection_id, name, position) " +
                "VALUES ('cat1', 'c1', 'Coffee', 0)",
            0,
        )
        driver.execute(
            null,
            "INSERT INTO checklist_item(id, collection_id, label, category_id, brand, position) " +
                "VALUES ('item1', 'c1', 'Boss Coffee', 'cat1', 'Suntory', 0)",
            0,
        )

        MementoDatabase.Schema.migrate(driver, 1, 2)

        val loaded = SQLiteCollectionRepository(driver).getCollection(CollectionId("c1"))
        assertEquals("Legacy", loaded?.name)
        assertNull(loaded?.metaAchievementName)
        assertNull(loaded?.metaAchievementDescription)
        assertEquals(25, loaded?.categories?.single()?.bonusPoints)
        assertEquals(10, loaded?.items?.single()?.points)
    }

    private fun createV1CollectionTables(driver: JdbcSqliteDriver) {
        driver.execute(
            null,
            """
            CREATE TABLE collection (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                description TEXT NOT NULL,
                created_at TEXT NOT NULL
            )
            """.trimIndent(),
            0,
        )
        driver.execute(
            null,
            """
            CREATE TABLE collection_category (
                id TEXT NOT NULL,
                collection_id TEXT NOT NULL,
                name TEXT NOT NULL,
                position INTEGER NOT NULL,
                PRIMARY KEY (collection_id, id)
            )
            """.trimIndent(),
            0,
        )
        driver.execute(
            null,
            """
            CREATE TABLE checklist_item (
                id TEXT NOT NULL,
                collection_id TEXT NOT NULL,
                label TEXT NOT NULL,
                category_id TEXT,
                brand TEXT,
                position INTEGER NOT NULL,
                PRIMARY KEY (collection_id, id)
            )
            """.trimIndent(),
            0,
        )
        driver.execute(
            null,
            """
            CREATE TABLE checklist_item_tag (
                collection_id TEXT NOT NULL,
                checklist_item_id TEXT NOT NULL,
                tag TEXT NOT NULL,
                position INTEGER NOT NULL,
                PRIMARY KEY (collection_id, checklist_item_id, position)
            )
            """.trimIndent(),
            0,
        )
    }
}
