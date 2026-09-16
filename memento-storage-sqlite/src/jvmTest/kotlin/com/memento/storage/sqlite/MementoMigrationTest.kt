package com.memento.storage.sqlite

import app.cash.sqldelight.db.QueryResult
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
        assertEquals(4L, MementoDatabase.Schema.version)
    }

    @Test
    fun migrateFromV1BackfillsDefaultsAndAllowsNewFields() = runTest {
        val driver = JdbcSqliteDriver(DatabaseDriverFactory.IN_MEMORY)
        createV1CollectionTables(driver)
        createLegacyMementoTable(driver)
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

        MementoDatabase.Schema.migrate(driver, 1L, MementoDatabase.Schema.version)

        val loaded = SQLiteCollectionRepository(driver).getCollection(CollectionId("c1"))
        assertEquals("Legacy", loaded?.name)
        assertNull(loaded?.metaAchievementName)
        assertNull(loaded?.metaAchievementDescription)
        assertEquals(25, loaded?.categories?.single()?.bonusPoints)
        assertEquals(10, loaded?.items?.single()?.points)
        assertNull(loaded?.items?.single()?.japaneseLabel)
    }

    @Test
    fun migrateToV3PreservesJapaneseLabelsWhenPresent() = runTest {
        val driver = JdbcSqliteDriver(DatabaseDriverFactory.IN_MEMORY)
        MementoDatabase.Schema.create(driver)
        driver.execute(
            null,
            "INSERT INTO collection(id, name, description, created_at) " +
                "VALUES ('c1', 'Konbini', 'drinks', '2024-01-01T00:00:00Z')",
            0,
        )
        driver.execute(
            null,
            "INSERT INTO checklist_item(id, collection_id, label, category_id, brand, japanese_label, position, points) " +
                "VALUES ('ayataka', 'c1', 'Ayataka', 'cat1', 'Coca-Cola', '綾鷹', 0, 10)",
            0,
        )

        val loaded = SQLiteCollectionRepository(driver).getCollection(CollectionId("c1"))
        assertEquals("綾鷹", loaded?.items?.single()?.japaneseLabel)
    }

    @Test
    fun migrateToV4DropsReflectionColumnAndPreservesMementos() = runTest {
        val driver = JdbcSqliteDriver(DatabaseDriverFactory.IN_MEMORY)
        createLegacyMementoTable(driver)
        driver.execute(
            null,
            "INSERT INTO memento(id, title, reflection, occurred_at, created_at, updated_at) " +
                "VALUES ('m1', 'Tokyo Konbini', 'legacy reflection', " +
                "'2024-01-01T00:00:00Z', '2024-01-01T00:00:00Z', '2024-01-01T00:00:00Z')",
            0,
        )

        MementoDatabase.Schema.migrate(driver, 3L, MementoDatabase.Schema.version)

        // The rebuilt table has no `reflection` column, so an insert that omits it must succeed.
        driver.execute(
            null,
            "INSERT INTO memento(id, title, occurred_at, created_at, updated_at) " +
                "VALUES ('m2', 'Kyoto', '2024-02-01T00:00:00Z', '2024-02-01T00:00:00Z', '2024-02-01T00:00:00Z')",
            0,
        )

        assertEquals("Tokyo Konbini", titleOf(driver, "m1"))
        assertEquals("Kyoto", titleOf(driver, "m2"))
    }

    private fun titleOf(driver: JdbcSqliteDriver, id: String): String? =
        driver.executeQuery(
            identifier = null,
            sql = "SELECT title FROM memento WHERE id = ?",
            mapper = { cursor ->
                cursor.next()
                QueryResult.Value(cursor.getString(0))
            },
            parameters = 1,
        ) { bindString(0, id) }.value

    private fun createLegacyMementoTable(driver: JdbcSqliteDriver) {
        driver.execute(
            null,
            """
            CREATE TABLE memento (
                id TEXT NOT NULL PRIMARY KEY,
                title TEXT NOT NULL,
                reflection TEXT NOT NULL,
                latitude REAL,
                longitude REAL,
                accuracy_meters REAL,
                place_name TEXT,
                place_brand TEXT,
                place_neighborhood TEXT,
                place_city TEXT,
                place_country TEXT,
                rating INTEGER,
                tasting_text TEXT,
                price_minor_units INTEGER,
                currency_code TEXT,
                occurred_at TEXT NOT NULL,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL
            )
            """.trimIndent(),
            0,
        )
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
