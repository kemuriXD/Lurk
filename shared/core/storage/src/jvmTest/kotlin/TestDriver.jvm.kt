package io.github.kemurixd.lurk.core.storage

import app.cash.sqldelight.db.SqlDriver

internal actual fun inMemoryDriver(): SqlDriver = createJvmDriver()
