package io.github.kemurixd.lurk

import android.app.Application
import co.touchlab.kermit.Logger
import co.touchlab.kermit.platformLogWriter
import io.github.kemurixd.lurk.auth.SessionRepository
import io.github.kemurixd.lurk.core.common.ScrubbingLogWriter
import io.github.kemurixd.lurk.core.config.RemoteConfigRepository
import io.github.kemurixd.lurk.di.appModules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class LurkApplication : Application() {
    private val scope = startupScope()
    private val restored = MutableStateFlow(false)

    /** True once the stored sessions have been read, so the start destination can be picked. The splash waits on it. */
    val sessionsRestored: StateFlow<Boolean> = restored.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        Logger.setLogWriters(ScrubbingLogWriter(platformLogWriter()))
        val koin =
            startKoin {
                androidContext(this@LurkApplication)
                modules(appModules(this@LurkApplication))
            }.koin
        scope.launch {
            // A failed restore still lets the app start, signed out.
            try {
                koin.get<SessionRepository>().restore()
            } finally {
                restored.value = true
            }
        }
        scope.launch {
            val config = koin.get<RemoteConfigRepository>()
            config.load()
            config.refreshIfDue()
        }
    }
}
