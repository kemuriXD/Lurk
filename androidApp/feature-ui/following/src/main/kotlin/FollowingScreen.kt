package io.github.kemurixd.lurk.featureui.following

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.designsystem.component.ChannelRow
import io.github.kemurixd.lurk.designsystem.component.EmptyState
import io.github.kemurixd.lurk.designsystem.component.ExpandedStreamCard
import io.github.kemurixd.lurk.designsystem.component.ExpandedStreamCardPlaceholder
import io.github.kemurixd.lurk.designsystem.component.PlatformFilterChips
import io.github.kemurixd.lurk.designsystem.component.SectionHeader
import io.github.kemurixd.lurk.designsystem.component.labelRes
import io.github.kemurixd.lurk.designsystem.component.platformErrors
import io.github.kemurixd.lurk.designsystem.time.rememberNowState
import io.github.kemurixd.lurk.feature.common.PlatformSlice
import io.github.kemurixd.lurk.feature.common.key
import io.github.kemurixd.lurk.feature.following.FollowingIntent
import io.github.kemurixd.lurk.feature.following.FollowingUiState
import io.github.kemurixd.lurk.feature.following.FollowingViewModel
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Instant
import io.github.kemurixd.lurk.designsystem.R as DesignR

private const val PLACEHOLDER_COUNT = 3
private const val FOLDED_DEGREES = -90f

/** Phones get one big live card a row; tablets two or three. */
private val LiveCardMinWidth = 360.dp

/**
 * Gets the [FollowingViewModel] and hands the state to [FollowingScreen]. It sends no reload of its own: the view model
 * reloads quietly whenever the screen starts collecting again, so a return from another tab or from the background
 * needs no intent, and an entry that merely recomposes or re-enters can never trigger one.
 */
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
    val pullState = rememberPullToRefreshState()
    // One ticking clock for every offline row, instead of a state and a timer started by each row that scrolls in. Only
    // the offline rows read it, so a tick redraws their text and leaves the screen and the live cards alone.
    val now = rememberNowState()
    val grid = rememberLazyGridState()
    // One fold state for every offline row: they grow in and shrink out together, and stay in the list until the
    // shrinking is over. Only the rows on screen are composed, so only those animate.
    val offlineFold =
        rememberOfflineFold(grid, state.offlineCollapsed, firstRowKey = state.offline.firstOrNull()?.key)
    Column(modifier.fillMaxSize()) {
        PlatformFilterChips(
            selected = state.filter,
            onSelect = { onIntent(FollowingIntent.SelectFilter(it)) },
            modifier = Modifier.padding(vertical = 8.dp),
        )
        PullToRefreshBox(
            // Only a reload the user asked for shows a spinner; the timed and the coming-back reloads stay quiet.
            isRefreshing = state.manualRefresh,
            onRefresh = { onIntent(FollowingIntent.Refresh) },
            modifier = Modifier.weight(1f),
            state = pullState,
            indicator = {
                if (state.manualRefresh || pullState.distanceFraction > 0f) {
                    PullToRefreshDefaults.Indicator(
                        state = pullState,
                        isRefreshing = state.manualRefresh,
                        modifier = Modifier.align(Alignment.TopCenter).testTag(FollowingTestTags.REFRESH_INDICATOR),
                    )
                }
            },
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = LiveCardMinWidth),
                state = grid,
                modifier = Modifier.fillMaxSize().testTag(FollowingTestTags.LIST),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                content(state, now, offlineFold, onIntent, onOpenPlayer, onOpenChannel, onSignIn, onOpenBrowse)
            }
        }
    }
}

private fun LazyGridScope.content(
    state: FollowingUiState,
    now: State<Instant>,
    offlineFold: OfflineFold,
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
        (
            state.refreshErrors +
                slices.mapNotNull { (platform, slice) ->
                    (slice as? PlatformSlice.Failed)?.let { platform to it.error }
                }
        ).toList()
    val loading = slices.values.any { it == PlatformSlice.Loading }
    val loaded = slices.values.any { it is PlatformSlice.Loaded<*> }
    val hasItems = state.live.isNotEmpty() || state.offline.isNotEmpty()

    // Nothing loaded anywhere: one full-screen state. Otherwise a banner per failed platform.
    val nothingLoaded = !hasItems && !loading && !loaded
    platformErrors(failures, nothingLoaded, R.string.following_error_title) { onIntent(FollowingIntent.Retry(it)) }
    signedOut.forEach { platform ->
        fullWidth("sign_in:${platform.name}") { SignInPrompt(platform, onSignIn) }
    }

    if (!hasItems && loading) {
        fullWidth("live_header") { SectionHeader(stringResource(R.string.section_live)) }
        items(PLACEHOLDER_COUNT, key = { "placeholder:$it" }) {
            ExpandedStreamCardPlaceholder()
        }
        return
    }
    if (state.live.isNotEmpty()) {
        fullWidth("live_header") {
            SectionHeader(stringResource(R.string.section_live), count = state.live.size) {
                FollowingSortMenu(state.sort, onSelect = { onIntent(FollowingIntent.SelectSort(it)) })
            }
        }
        items(state.live, key = { it.key }) { stream ->
            ExpandedStreamCard(
                stream = stream,
                onOpenPlayer = { onOpenPlayer(stream) },
                onOpenChannel = { onOpenChannel(stream.channel) },
            )
        }
    } else if (loaded && !loading) {
        fullWidth("nobody_live") { NobodyLive(onOpenBrowse) }
    }
    offlineSection(state, now, offlineFold, onIntent, onOpenChannel)
}

private fun LazyGridScope.offlineSection(
    state: FollowingUiState,
    now: State<Instant>,
    fold: OfflineFold,
    onIntent: (FollowingIntent) -> Unit,
    onOpenChannel: (ChannelRef) -> Unit,
) {
    if (state.offline.isEmpty()) return
    fullWidth(OFFLINE_HEADER_KEY) { OfflineHeader(state.offline.size, state.offlineCollapsed, onIntent) }
    // Folded and finished shrinking, no offline row is composed at all, so none asks for a last-live lookup.
    if (!state.offlineCollapsed || fold.rowsInList) offlineRows(state, now, fold, onIntent, onOpenChannel)
}

private fun LazyGridScope.offlineRows(
    state: FollowingUiState,
    now: State<Instant>,
    fold: OfflineFold,
    onIntent: (FollowingIntent) -> Unit,
    onOpenChannel: (ChannelRef) -> Unit,
) {
    val expanded = !state.offlineCollapsed
    items(state.offline, key = { it.key }, span = { GridItemSpan(maxLineSpan) }) { channel ->
        // Composed rows only: the lazy grid composes what is on screen, so off-screen rows never cost a lookup, and
        // a row that scrolls away before its turn lets go of it.
        // A row that is shrinking away has no use for its lookup, so it lets go the moment the fold starts.
        if (channel.lastStream?.startedAt == null) {
            DisposableEffect(channel.ref, expanded) {
                if (expanded) onIntent(FollowingIntent.OfflineRowShown(channel.ref))
                onDispose { if (expanded) onIntent(FollowingIntent.OfflineRowHidden(channel.ref)) }
            }
        }
        // Created closed while the section is still unfolding, so the row grows in; open once it has settled.
        val shown = remember { MutableTransitionState(fold.settledExpanded) }
        SideEffect { shown.targetState = expanded }
        AnimatedVisibility(
            visibleState = shown,
            enter = expandVertically(tween(OFFLINE_FOLD_MILLIS)) + fadeIn(tween(OFFLINE_FOLD_MILLIS)),
            exit = shrinkVertically(tween(OFFLINE_FOLD_MILLIS)) + fadeOut(tween(OFFLINE_FOLD_MILLIS)),
        ) {
            ChannelRow(channel = channel, onClick = { onOpenChannel(channel.ref) }, now = { now.value })
        }
    }
}

private fun LazyGridScope.fullWidth(
    key: String,
    content: @Composable () -> Unit,
) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

/** The Offline header. Tapping it, or its arrow, folds or unfolds the offline rows; the count stays either way. */
@Composable
private fun OfflineHeader(
    count: Int,
    collapsed: Boolean,
    onIntent: (FollowingIntent) -> Unit,
) {
    val state = stringResource(if (collapsed) R.string.state_collapsed else R.string.state_expanded)
    SectionHeader(
        text = stringResource(R.string.section_offline),
        count = count,
        onClick = { onIntent(FollowingIntent.ToggleOffline) },
        modifier = Modifier.semantics { stateDescription = state },
    ) {
        val turn by animateFloatAsState(if (collapsed) FOLDED_DEGREES else 0f, label = "offline arrow")
        Icon(
            painter = painterResource(DesignR.drawable.ic_expand_more),
            contentDescription = stringResource(if (collapsed) R.string.cd_show_offline else R.string.cd_hide_offline),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            // Read in the layer, so the turn redraws the arrow and recomposes nothing.
            modifier = Modifier.padding(end = 8.dp).size(24.dp).graphicsLayer { rotationZ = turn },
        )
    }
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
                Text(stringResource(R.string.sign_in_to, stringResource(platform.labelRes)))
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
        Text(stringResource(R.string.sign_in_to, stringResource(platform.labelRes)))
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
