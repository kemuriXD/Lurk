package io.github.kemurixd.lurk.core.storage

import io.github.kemurixd.lurk.core.storage.db.LurkDatabase
import org.koin.core.module.Module
import org.koin.dsl.module

/** Platform-neutral storage bindings. The platform module supplies the `SqlDriver`, `DataStore` and [SecureStore]. */
val storageModule: Module =
    module {
        single { LurkDatabase(get()) }
        single { CacheStore(get(), get()) }
        single { SettingsStore(get()) }
    }
