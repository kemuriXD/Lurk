package io.github.kemurixd.lurk.di

import android.content.Context
import io.github.kemurixd.lurk.core.common.commonModule
import io.github.kemurixd.lurk.core.config.configModule
import io.github.kemurixd.lurk.core.network.networkModule
import io.github.kemurixd.lurk.core.storage.androidStorageModule
import io.github.kemurixd.lurk.core.storage.storageModule
import org.koin.core.module.Module

/** Every Koin module the app starts with. Feature and platform modules join this list as they are built. */
fun appModules(context: Context): List<Module> =
    listOf(
        commonModule,
        networkModule,
        storageModule,
        androidStorageModule(context),
        configModule,
    )
