package io.github.kemurixd.lurk.core.storage

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.kemurixd.lurk.core.storage.db.LurkDatabase
import java.util.Properties

/** A JDBC SQLite driver with the schema created; in memory unless [url] names a file. */
fun createJvmDriver(url: String = JdbcSqliteDriver.IN_MEMORY): SqlDriver =
    JdbcSqliteDriver(url, Properties(), LurkDatabase.Schema)
