package io.github.kemurixd.lurk.emotes

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.kemurixd.lurk.core.storage.db.LurkDatabase
import java.util.Properties

internal actual fun inMemoryDriver(): SqlDriver =
    JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties(), LurkDatabase.Schema)
