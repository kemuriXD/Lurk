package io.github.kemurixd.lurk.core.config

import app.cash.sqldelight.db.SqlDriver

/** A fresh in-memory cache database driver with the schema already created. */
internal expect fun inMemoryDriver(): SqlDriver
