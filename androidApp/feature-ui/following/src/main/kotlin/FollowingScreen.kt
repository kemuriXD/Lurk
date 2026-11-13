package io.github.kemurixd.lurk.featureui.following

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.designsystem.component.ChannelRow
import io.github.kemurixd.lurk.designsystem.component.EmptyState
import io.github.kemurixd.lurk.designsystem.component.InlineErrorChip
import io.github.kemurixd.lurk.designsystem.component.PlatformFilterChips
import io.github.kemurixd.lurk.designsystem.component.SectionHeader
import io.github.kemurixd.lurk.designsystem.component.StreamCard
import io.github.kemurixd.lurk.designsystem.component.StreamCardPlaceholder
import io.github.kemurixd.lurk.feature.common.PlatformSlice
import io.github.kemurixd.lurk.feature.common.key
import io.github.kemurixd.lurk.feature.following.FollowingIntent
import io.github.kemurixd.lurk.feature.following.FollowingUiState
import io.github.kemurixd.lurk.feature.following.FollowingViewModel
import org.koin.compose.viewmodel.koinViewModel
import io.github.kemurixd.lurk.designsystem.R as DesignR

private const val PLACEHOLDER_COUNT = 5
private val CardPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)

/** Gets the [FollowingViewModel], reloads on every resume and hands the state to [FollowingScreen]. */
@Composable
fun FollowingEntry(
    onOpenPlayer: (Stream) -> Unit,
    onOpenChannel: (ChannelRef) -> Unit,
    onSignIn: (Platform) -> Unit,
    onOpenBrowse: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FollowingViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.onIntent(FollowingIntent.Refresh)
        onPauseOrDispose {}
    }
    FollowingScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onOpenPlayer = onOpenPlayer,
        onOpenChannel = onOpenChannel,
        onSignIn = onSignIn,
        onOpenBrowse = onOpenBrowse,
        modifier = modifier,
    )
}

/**
 * The Following list: filter chips, then Live and Offline sections. Which half of the screen shows is decided from
 * the slices, never from `stale`, so the minute refresh doesn't flicker anything.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowingScreen(
    state: FollowingUiState,
    onIntent: (FollowingIntent) -> Unit,
    onOpenPlayer: (Stream) -> Unit,
    onOpenChannel: (ChannelRef) -> Unit,
    onSignIn: (Platform) -> Unit,
    onOpenBrowse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        PlatformFilterChips(
            selected = state.filter,
            onSelect = { onIntent(FollowingIntent.SelectFilter(it)) },
            modifier = Modifier.padding(vertical = 8.dp),
        )
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { onIntent(FollowingIntent.Refresh) },
            modifier = Modifier.weight(1f),
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 300.dp),
                modifier = Modifier.fillMaxSize().testTag(FollowingTestTags.LIST),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                content(state, onIntent, onOpenPlayer, onOpenChannel, onSignIn, onOpenBrowse)
            }
        }
    }
}

private fun LazyGridScope.content(
    state: FollowingUiState,
    onIntent: (FollowingIntent) -> Unit,
    onOpenPlayer: (Stream) -> Unit,
    onOpenChannel: (ChannelRef) -> Unit,
    onSignIn: (Platform) -> Unit,
    onOpenBrowse: () -> Unit,
) {
    val slices = state.slices
    val signedOut = slices.filterValues { it == PlatformSlice.NotSignedIn }.keys.toList()
    if (slices.isNotEmpty() && signedOut.size == slices.size) {
        fullWidth("signed_out") { SignedOut(signedOut, onSignIn) }
        return
    }
    val failures =
        state.refreshErrors +
            slices.mapNotNull { (platform, slice) -> (slice as? PlatformSlice.Failed)?.let { platform to it.error } }
    val loading = slices.values.any { it == PlatformSlice.Loading }
    val loaded = slices.values.any { it is PlatformSlice.Loaded<*> }
    val hasItems = state.live.isNotEmpty() || state.offline.isNotEmpty()

    failures.forEach { (platform, error) ->
        fullWidth("error:${platform.name}") { ErrorChip(platform, error) { onIntent(FollowingIntent.Retry(platform)) } }
    }
    signedOut.forEach { platform ->
        fullWidth("sign_in:${platform.name}") { SignInPrompt(platform, onSignIn) }
    }

    if (!hasItems && loading) {
        fullWidth("live_header") { SectionHeader(stringResource(R.string.section_live)) }
        items(PLACEHOLDER_COUNT, key = { "placeholder:$it" }) {
            StreamCardPlaceholder(Modifier.padding(CardPadding))
        }
        return
    }
    if (state.live.isNotEmpty()) {
        fullWidth("live_header") {
            SectionHeader(stringResource(R.string.section_live)) {
                Count(state.live.size, FollowingTestTags.LIVE_COUNT)
                FollowingSortMenu(state.sort, onSelect = { onIntent(FollowingIntent.SelectSort(it)) })
            }
        }
        items(state.live, key = { it.key }) { stream ->
            StreamCard(
                stream = stream,
                onOpenPlayer = { onOpenPlayer(stream) },
                onOpenChannel = { onOpenChannel(stream.channel) },
                modifier = Modifier.padding(CardPadding),
            )
        }
    } else if (loaded && !loading) {
        fullWidth("nobody_live") { NobodyLive(onOpenBrowse) }
    }
    if (state.offline.isNotEmpty()) {
        fullWidth("offline_header") {
            SectionHeader(
                stringResource(R.string.section_offline),
            ) { Count(state.offline.size, FollowingTestTags.OFFLINE_COUNT) }
        }
        items(state.offline, key = { it.key }, span = { GridItemSpan(maxLineSpan) }) { channel ->
            ChannelRow(channel = channel, onClick = { onOpenChannel(channel.ref) })
        }
    }
}

private fun LazyGridScope.fullWidth(
    key: String,
    content: @Composable () -> Unit,
) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
private fun Count(
    count: Int,
    tag: String,
) {
    Text(
        text = count.toString(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag(tag),
    )
}

@Composable
private fun ErrorChip(
    platform: Platform,
    error: LurkError,
    onRetry: () -> Unit,
) {
    InlineErrorChip(
        platform = platform,
        error = error,
        onRetry = onRetry,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun SignedOut(
    platforms: List<Platform>,
    onSignIn: (Platform) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EmptyState(
            title = stringResource(R.string.signed_out_title),
            body = stringResource(R.string.signed_out_body),
            action = null,
            icon = DesignR.drawable.ic_login,
        )
        platforms.forEach { platform ->
            Button(onClick = { onSignIn(platform) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.sign_in_to, platform.displayName()))
            }
        }
    }
}

@Composable
private fun SignInPrompt(
    platform: Platform,
    onSignIn: (Platform) -> Unit,
) {
    TextButton(
        onClick = { onSignIn(platform) },
        modifier = Modifier.padding(horizontal = 8.dp).heightIn(min = 48.dp),
    ) {
        Text(stringResource(R.string.sign_in_to, platform.displayName()))
    }
}

@Composable
private fun NobodyLive(onOpenBrowse: () -> Unit) {
    EmptyState(
        title = stringResource(R.string.nobody_live_title),
        body = stringResource(R.string.nobody_live_body),
        action = stringResource(R.string.browse_live_channels) to onOpenBrowse,
        modifier = Modifier.padding(vertical = 16.dp),
    )
}

@Composable
private fun Platform.displayName(): String =
    stringResource(
        when (this) {
            Platform.TWITCH -> DesignR.string.platform_twitch
            Platform.KICK -> DesignR.string.platform_kick
        },
    )
