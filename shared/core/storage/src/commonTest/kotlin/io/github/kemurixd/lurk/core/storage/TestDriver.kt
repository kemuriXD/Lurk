package io.github.kemurixd.lurk.core.storage

import app.cash.sqldelight.db.SqlDriver

/** A fresh in-memory database driver with the schema already created. */
internal expect fun inMemoryDriver(): SqlDriver
