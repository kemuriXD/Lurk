package io.github.kemurixd.lurk.featureui.channel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kemurixd.lurk.core.model.Channel
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.designsystem.component.EmptyState
import io.github.kemurixd.lurk.designsystem.component.formatViewers
import io.github.kemurixd.lurk.designsystem.messageRes
import io.github.kemurixd.lurk.designsystem.time.rememberNow
import io.github.kemurixd.lurk.feature.channel.ChannelIntent
import io.github.kemurixd.lurk.feature.channel.ChannelUiState
import io.github.kemurixd.lurk.feature.channel.ChannelViewModel
import io.github.kemurixd.lurk.feature.common.PlatformSlice
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.Instant
import io.github.kemurixd.lurk.designsystem.R as DesignR

private val BodyMaxWidth = 640.dp

/**
 * Gets the [ChannelViewModel] for one channel, acts on its effects and hands its state to [ChannelScreen]. [onSignIn]
 * is for the signed-out Follow button.
 */
@Composable
fun ChannelEntry(
    platform: Platform,
    login: String,
    onOpenPlayer: (Stream) -> Unit,
    onSignIn: (Platform) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChannelViewModel = koinViewModel { parametersOf(platform, login) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    HandleChannelEffects(
        effects = viewModel.effects,
        onOpenPlayer = onOpenPlayer,
        onError = { error ->
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar(context.getString(error.messageRes()))
            }
        },
    )
    ChannelScreen(
        ref = ChannelRef(platform, login),
        state = state,
        onIntent = viewModel::onIntent,
        onSignIn = { onSignIn(platform) },
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbar,
    )
}

/**
 * One channel (Figma Channel): banner with the back button, avatar and status, then bio, followers, the Follow and
 * Share buttons, and either the live preview or the last stream. It draws its own back button, so the app doesn't
 * wrap it in a top bar. [now] is what "last live 2 days ago" counts from.
 */
@Composable
fun ChannelScreen(
    ref: ChannelRef,
    state: ChannelUiState,
    onIntent: (ChannelIntent) -> Unit,
    onSignIn: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    now: Instant = rememberNow(),
) {
    val channel = (state.load as? PlatformSlice.Loaded)?.value
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            ChannelHeader(ref = ref, channel = channel, now = now, onBack = onBack)
            Column(
                modifier = Modifier.widthIn(max = BodyMaxWidth).fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (val load = state.load) {
                    PlatformSlice.Loading, PlatformSlice.NotSignedIn -> {
                        LoadingBody()
                    }

                    is PlatformSlice.Failed -> {
                        EmptyState(
                            title = stringResource(R.string.load_error_title),
                            body = stringResource(load.error.messageRes()),
                            action = stringResource(R.string.try_again) to { onIntent(ChannelIntent.Retry) },
                        )
                    }

                    is PlatformSlice.Loaded -> {
                        ChannelBody(load.value, state, onIntent, onSignIn)
                    }
                }
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun LoadingBody() {
    val loading = stringResource(DesignR.string.loading)
    Box(
        Modifier.fillMaxWidth().padding(vertical = 48.dp).semantics { contentDescription = loading },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ChannelBody(
    channel: Channel,
    state: ChannelUiState,
    onIntent: (ChannelIntent) -> Unit,
    onSignIn: () -> Unit,
) {
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        channel.bio?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        channel.followers?.let {
            Text(
                text = stringResource(R.string.followers_count, formatViewers(it)),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        FollowActions(state.follow, onToggle = { onIntent(ChannelIntent.ToggleFollow) }, onShare = {
            onIntent(ChannelIntent.Share)
        }, onSignIn = onSignIn)
        val live = channel.live
        if (live != null) {
            SectionTitle(stringResource(R.string.section_live_now))
            LivePreview(live, channel.displayName, onClick = { onIntent(ChannelIntent.WatchLive) })
        } else {
            SectionTitle(stringResource(R.string.section_last_stream))
            LastStreamCard(channel)
        }
        Spacer(Modifier.height(12.dp))
    }
}
