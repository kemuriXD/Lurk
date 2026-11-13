package io.github.kemurixd.lurk.featureui.channel

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.feature.channel.ChannelEffect
import kotlinx.coroutines.flow.Flow

/**
 * Acts on the ViewModel's one-shot effects while the screen is started: links open in a Custom Tab, shares go through
 * the system share sheet. Collecting stops below STARTED, so an effect sent meanwhile waits in the ViewModel and is
 * handled once, when the screen is back, never twice. [onError] gets a failed follow or unfollow; [onCouldNotOpen] a
 * link (false) or share (true) that nothing on the device could open.
 */
@Composable
fun HandleChannelEffects(
    effects: Flow<ChannelEffect>,
    onOpenPlayer: (Stream) -> Unit,
    onError: (LurkError) -> Unit,
    onCouldNotOpen: (share: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val openPlayer by rememberUpdatedState(onOpenPlayer)
    val showError by rememberUpdatedState(onError)
    val couldNotOpen by rememberUpdatedState(onCouldNotOpen)
    LaunchedEffect(effects, lifecycle, context) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            effects.collect { effect ->
                when (effect) {
                    is ChannelEffect.OpenExternal -> {
                        if (!openInCustomTab(context, effect.url)) couldNotOpen(false)
                    }

                    is ChannelEffect.ShareText -> {
                        if (!shareText(context, effect.text)) couldNotOpen(true)
                    }

                    is ChannelEffect.OpenPlayer -> {
                        openPlayer(effect.stream)
                    }

                    is ChannelEffect.ShowError -> {
                        showError(effect.error)
                    }
                }
            }
        }
    }
}

/** Opens [url] in a Custom Tab, which falls back to the browser. False if nothing can open it. */
internal fun openInCustomTab(
    context: Context,
    url: String,
): Boolean =
    try {
        CustomTabsIntent.Builder().build().launchUrl(context, url.toUri())
        true
    } catch (_: ActivityNotFoundException) {
        false
    }

/** Shows the system share sheet for [text]. False if there is nothing to share with. */
internal fun shareText(
    context: Context,
    text: String,
): Boolean {
    val send =
        Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text)
    return try {
        context.startActivity(Intent.createChooser(send, null))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
