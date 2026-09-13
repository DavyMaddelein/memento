package com.memento.storage.sqlite

import app.cash.sqldelight.db.SqlDriver

/**
 * Creates a platform [SqlDriver] for the memento SQLite database.
 *
 * Platform construction differs (Android needs a [android.content.Context], JVM takes a JDBC
 * URL), so the constructor lives on the actual declarations.
 */
expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}
