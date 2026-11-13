package io.github.kemurixd.lurk.feature.channel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kemurixd.lurk.auth.AccountState
import io.github.kemurixd.lurk.core.common.DispatcherProvider
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Followed
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.feature.common.AccountsState
import io.github.kemurixd.lurk.feature.common.PlatformSlice
import io.github.kemurixd.lurk.feature.common.key
import io.github.kemurixd.lurk.feature.common.loadBoth
import io.github.kemurixd.lurk.platform.api.StreamingPlatform
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.kemurixd.lurk.core.model.Channel as ChannelProfile

/**
 * One channel's page. It always loads `platform.channel(ref)` first: that call resolves the IDs Kick leaves blank in
 * lists, and `setFollow` is given that resolved channel.
 *
 * The Follow button follows the account. Signed out is [FollowState.Hidden], at once, even while the page is open.
 * Signed in, the follow list says [FollowState.Following] or [FollowState.NotFollowing] (matched by platform and
 * login), or [FollowState.Unknown] when it can't be read. On Twitch the API can't change follows, so
 * [ChannelIntent.ToggleFollow] opens the channel on twitch.tv. On Kick it sends the change, shown as
 * [FollowState.Busy] meanwhile, and a failure puts the old state back and emits [ChannelEffect.ShowError]. A toggle
 * while one is running is ignored. A result for an account that has since signed out is dropped.
 *
 * Loading starts when [state] gets a collector. Five seconds after the last one leaves, the cached value resets; a
 * loaded channel is not fetched again when a collector returns, but the follow list is.
 *
 * [effects] hands each effect to one collector, once; an effect sent while nobody collects waits for the next one.
 * Call [onIntent] from the main thread.
 */
class ChannelViewModel(
    private val platform: StreamingPlatform,
    private val ref: ChannelRef,
    private val accounts: AccountsState,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    /**
     * Everything that decides the screen, in one value. [owner] is the account [membership] and [busy] belong to;
     * [membership] is null while unknown; [busy] is the target of the running follow change.
     */
    private val data = MutableStateFlow(Data())

    private val effectChannel = Channel<ChannelEffect>(Channel.BUFFERED)

    /** One-shot effects. Never replayed: a collector that returns sees only what is sent after it. */
    val effects: Flow<ChannelEffect> = effectChannel.receiveAsFlow()

    // The running loads, by kind. A job that is no longer the one stored here was replaced and drops its result.
    private var channelJob: Job? = null
    private var followedJob: Job? = null
    private var toggleJob: Job? = null

    private val owners: Flow<String?> = accounts.states.map { ownerOf(platform.id, it) }.distinctUntilChanged()

    /** Emits nothing: it loads the channel once, then loads the follow list whenever the account changes. */
    private val autoLoad: Flow<Nothing> =
        flow {
            if (data.value.slice !is PlatformSlice.Loaded<*>) loadChannel()
            owners.collectLatest(::accountChanged)
        }

    val state: StateFlow<ChannelUiState> =
        merge(combine(data, owners, ::uiState), autoLoad).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS, replayExpirationMillis = 0),
            initialValue = uiState(data.value, ownerOf(platform.id, accounts.states.value)),
        )

    fun onIntent(intent: ChannelIntent) {
        when (intent) {
            ChannelIntent.ToggleFollow -> toggleFollow()
            ChannelIntent.Share -> effectChannel.trySend(ChannelEffect.ShareText(channelUrl(ref)))
            ChannelIntent.Retry -> retry()
            ChannelIntent.WatchLive -> watchLive()
        }
    }

    private fun currentOwner(): String? = ownerOf(platform.id, accounts.states.value)

    private fun retry() {
        loadChannel()
        currentOwner()?.let(::loadFollowed)
    }

    private fun watchLive() {
        val live = (data.value.slice as? PlatformSlice.Loaded)?.value?.live ?: return
        effectChannel.trySend(ChannelEffect.OpenPlayer(live))
    }

    private fun accountChanged(owner: String?) {
        if (data.value.owner != owner) {
            toggleJob?.cancel()
            toggleJob = null
            followedJob?.cancel()
            data.update { it.copy(owner = owner, membership = null, busy = null) }
        }
        if (owner != null) loadFollowed(owner)
    }

    private fun loadChannel() {
        channelJob?.cancel()
        data.update { it.copy(slice = PlatformSlice.Loading) }
        val job =
            viewModelScope.launch(start = CoroutineStart.LAZY) {
                val me = currentCoroutineContext().job
                val result =
                    withContext(dispatchers.io) { loadBoth(listOf(platform)) { it.channel(ref) } }.getValue(platform.id)
                if (channelJob === me) data.update { it.copy(slice = result) }
            }
        channelJob = job
        job.start()
    }

    private fun loadFollowed(owner: String) {
        followedJob?.cancel()
        val job =
            viewModelScope.launch(start = CoroutineStart.LAZY) {
                val me = currentCoroutineContext().job
                val result =
                    withContext(dispatchers.io) { loadBoth(listOf(platform)) { it.followed() } }.getValue(platform.id)
                // With nobody collecting an account change doesn't cancel this job, so check the account here too.
                if (followedJob === me && currentOwner() == owner) {
                    val membership = (result as? PlatformSlice.Loaded)?.value?.contains(ref)
                    data.update { it.copy(owner = owner, membership = membership) }
                }
            }
        followedJob = job
        job.start()
    }

    private fun toggleFollow() {
        val owner = currentOwner()
        val channel = (data.value.slice as? PlatformSlice.Loaded)?.value
        when {
            owner == null || toggleJob?.isActive == true -> Unit
            !platform.capabilities.followMutation -> effectChannel.trySend(ChannelEffect.OpenExternal(channelUrl(ref)))
            channel != null -> startFollowChange(owner, channel)
        }
    }

    private fun startFollowChange(
        owner: String,
        channel: ChannelProfile,
    ) {
        val before = data.value
        val target = !(before.owner == owner && before.membership == true)
        // A follow list that lands now would be older than this change.
        followedJob?.cancel()
        data.update {
            it.copy(owner = owner, membership = it.membership.takeIf { _ -> it.owner == owner }, busy = target)
        }
        val job =
            viewModelScope.launch(start = CoroutineStart.LAZY) {
                val me = currentCoroutineContext().job
                val result =
                    withContext(dispatchers.io) { loadBoth(listOf(platform)) { it.setFollow(channel, target) } }
                        .getValue(platform.id)
                if (toggleJob === me) {
                    toggleJob = null
                    finishToggle(owner, target, result)
                }
            }
        toggleJob = job
        job.start()
    }

    private fun finishToggle(
        owner: String,
        target: Boolean,
        result: PlatformSlice<Unit>,
    ) {
        if (currentOwner() != owner) {
            data.update { it.copy(busy = null) }
            return
        }
        if (result is PlatformSlice.Loaded) {
            data.update { it.copy(membership = target, busy = null) }
        } else {
            data.update { it.copy(busy = null) }
            val error = (result as? PlatformSlice.Failed)?.error ?: LurkError.NotSignedIn
            effectChannel.trySend(ChannelEffect.ShowError(error))
        }
    }

    private data class Data(
        val slice: PlatformSlice<ChannelProfile> = PlatformSlice.Loading,
        val owner: String? = null,
        val membership: Boolean? = null,
        val busy: Boolean? = null,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L

        fun ownerOf(
            platform: Platform,
            states: Map<Platform, AccountState>,
        ): String? =
            when (val account = states[platform]) {
                null, AccountState.SignedOut -> null
                is AccountState.SignedIn -> account.account.userId.ifBlank { account.account.login }
                is AccountState.Expired -> account.account.userId.ifBlank { account.account.login }
            }

        fun Followed.contains(ref: ChannelRef): Boolean =
            live.any { it.key == ref.key } || offline.any { it.key == ref.key }

        fun uiState(
            data: Data,
            owner: String?,
        ): ChannelUiState {
            val follow =
                when {
                    owner == null -> FollowState.Hidden
                    data.owner != owner -> FollowState.Unknown
                    data.busy != null -> FollowState.Busy(data.busy)
                    data.membership == true -> FollowState.Following
                    data.membership == false -> FollowState.NotFollowing
                    else -> FollowState.Unknown
                }
            return ChannelUiState(load = data.slice, follow = follow)
        }
    }
}
