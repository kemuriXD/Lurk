package io.github.kemurixd.lurk

import android.app.Application
import co.touchlab.kermit.Logger
import co.touchlab.kermit.platformLogWriter
import io.github.kemurixd.lurk.core.common.ScrubbingLogWriter
import io.github.kemurixd.lurk.core.config.RemoteConfigRepository
import io.github.kemurixd.lurk.di.appModules
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class LurkApplication : Application() {
    private val scope = startupScope()

    override fun onCreate() {
        super.onCreate()
        Logger.setLogWriters(ScrubbingLogWriter(platformLogWriter()))
        val koin =
            startKoin {
                androidContext(this@LurkApplication)
                modules(appModules(this@LurkApplication))
            }.koin
        scope.launch {
            val config = koin.get<RemoteConfigRepository>()
            config.load()
            config.refreshIfDue()
        }
    }
}
