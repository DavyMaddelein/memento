package com.memento.storage.sqlite

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.memento.storage.sqlite.db.MementoDatabase

/**
 * iOS [DatabaseDriverFactory] backed by the bundled SQLite via [NativeSqliteDriver].
 *
 * The database lives under the app's Application Support directory, keyed by [databaseName].
 */
actual class DatabaseDriverFactory(
    private val databaseName: String = DEFAULT_DATABASE_NAME,
) {
    actual fun createDriver(): SqlDriver =
        NativeSqliteDriver(MementoDatabase.Schema, databaseName)

    private companion object {
        const val DEFAULT_DATABASE_NAME = "memento.db"
    }
}
