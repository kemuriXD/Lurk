package io.github.kemurixd.lurk.di

import android.content.Context
import co.touchlab.kermit.Logger
import io.github.kemurixd.lurk.auth.authModule
import io.github.kemurixd.lurk.chat.chatModule
import io.github.kemurixd.lurk.core.common.commonModule
import io.github.kemurixd.lurk.core.config.configModule
import io.github.kemurixd.lurk.core.network.networkModule
import io.github.kemurixd.lurk.core.storage.androidStorageModule
import io.github.kemurixd.lurk.core.storage.storageModule
import io.github.kemurixd.lurk.emotes.emotesModule
import io.github.kemurixd.lurk.platform.kick.kickModule
import io.github.kemurixd.lurk.platform.twitch.twitchModule
import io.github.kemurixd.lurk.playback.playbackModule
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

private val log = Logger.withTag("AppScope")

/**
 * The scope `authModule` and the platform modules use for work that outlives any screen.
 * A job that throws is logged by exception class only (messages can carry secrets) and does not end the process.
 */
val appScopeModule: Module =
    module {
        single(named("appScope")) {
            val handler =
                CoroutineExceptionHandler { _, e -> log.e { "Background task failed: ${e::class.simpleName}" } }
            CoroutineScope(SupervisorJob() + Dispatchers.Default + handler)
        }
    }

/** Every Koin module the app starts with. Feature and platform modules join this list as they are built. */
fun appModules(context: Context): List<Module> =
    listOf(
        commonModule,
        networkModule,
        storageModule,
        androidStorageModule(context),
        configModule,
        emotesModule,
        chatModule,
        playbackModule,
        appScopeModule,
        authModule,
        twitchModule,
        kickModule,
    )
