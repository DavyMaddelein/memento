package com.memento.storage.sqlite

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.memento.storage.sqlite.db.MementoDatabase

/**
 * JVM/desktop driver factory.
 *
 * @param jdbcUrl SQLite JDBC URL. Defaults to an in-memory database; pass a file URL
 *   (e.g. `jdbc:sqlite:/tmp/memento.db`) for on-disk persistence.
 */
actual class DatabaseDriverFactory(
    private val jdbcUrl: String = IN_MEMORY,
) {
    actual fun createDriver(): SqlDriver {
        val driver = JdbcSqliteDriver(jdbcUrl)
        MementoDatabase.Schema.create(driver)
        return driver
    }

    companion object {
        const val IN_MEMORY: String = "jdbc:sqlite::memory:"
    }
}
