package io.github.kemurixd.lurk.designsystem.component

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources

/** Counts which strings the content asks for. A composable asks for its strings again each time it recomposes. */
@Composable
internal fun CountingStrings(
    onRead: (Int) -> Unit,
    content: @Composable () -> Unit,
) {
    val base = LocalResources.current
    val counting = remember(base) { CountingResources(base, onRead) }
    CompositionLocalProvider(LocalResources provides counting, content = content)
}

@Suppress("DEPRECATION")
private class CountingResources(
    base: Resources,
    private val onRead: (Int) -> Unit,
) : Resources(base.assets, base.displayMetrics, base.configuration) {
    override fun getString(id: Int): String {
        onRead(id)
        return super.getString(id)
    }

    override fun getString(
        id: Int,
        vararg formatArgs: Any?,
    ): String {
        onRead(id)
        return super.getString(id, *formatArgs)
    }
}
