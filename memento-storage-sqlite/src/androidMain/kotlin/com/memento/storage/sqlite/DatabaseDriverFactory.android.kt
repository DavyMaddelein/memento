package com.memento.storage.sqlite

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.memento.storage.sqlite.db.MementoDatabase

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver = AndroidSqliteDriver(
        schema = MementoDatabase.Schema,
        context = context,
        name = DATABASE_NAME,
    )

    companion object {
        const val DATABASE_NAME: String = "memento.db"
    }
}
