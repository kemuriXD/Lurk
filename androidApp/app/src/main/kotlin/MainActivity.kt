package io.github.kemurixd.lurk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kemurixd.lurk.designsystem.theme.LurkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        val sessionsRestored = (application as LurkApplication).sessionsRestored
        splash.setKeepOnScreenCondition { !sessionsRestored.value }
        enableEdgeToEdge()
        setContent {
            val ready by sessionsRestored.collectAsStateWithLifecycle()
            // The start tab depends on the restored sessions, so nothing is composed before they are read.
            if (ready) LurkTheme { LurkApp() }
        }
    }
}
