package io.github.kemurixd.lurk.feature.following

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kemurixd.lurk.auth.AccountState
import io.github.kemurixd.lurk.core.common.DispatcherProvider
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.model.Channel
import io.github.kemurixd.lurk.core.model.Followed
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.core.storage.AppSettings
import io.github.kemurixd.lurk.core.storage.FollowingSortSetting
import io.github.kemurixd.lurk.core.storage.PlatformFilterSetting
import io.github.kemurixd.lurk.core.storage.SettingsStore
import io.github.kemurixd.lurk.feature.common.AccountsState
import io.github.kemurixd.lurk.feature.common.LastLiveLoader
import io.github.kemurixd.lurk.feature.common.PlatformFilter
import io.github.kemurixd.lurk.feature.common.PlatformSlice
import io.github.kemurixd.lurk.feature.common.loadBoth
import io.github.kemurixd.lurk.feature.common.sortedByViewers
import io.github.kemurixd.lurk.feature.common.withLastLive
import io.github.kemurixd.lurk.platform.api.StreamingPlatform
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The Following list: every platform's followed channels, loaded concurrently and merged.
 *
 * While [state] has a collector, every signed-in platform reloads every [refreshInterval], and again whenever a
 * platform's account changes. Five seconds after the last collector leaves, the loop stops and the cached state resets.
 * A platform that is still loading for the same account is never asked twice, whether the request comes from the
 * loop, [FollowingIntent.Refresh] or [FollowingIntent.Retry].
 *
 * Every load result is tagged with the account that produced it. Data from any other account is never shown: a
 * signed-out platform shows `NotSignedIn`, a switched one shows `Loading` until the new account's load lands, and a
 * load that finishes after its account left is dropped.
 *
 * Offline rows whose platform leaves out when they were last live get the time from [lastLive], only once the screen
 * reports them with [FollowingIntent.OfflineRowShown].
 *
 * Call [onIntent] from the main thread.
 */
class FollowingViewModel(
    private val platforms: List<StreamingPlatform>,
    private val accounts: AccountsState,
    private val settings: SettingsStore,
    private val dispatchers: DispatcherProvider,
    private val lastLive: LastLiveLoader = LastLiveLoader(platforms, dispatchers),
    private val refreshInterval: Duration = 60.seconds,
) : ViewModel() {
    /** Every platform's load results in one value, so the UI never sees a slice and its error out of step. */
    private val loads = MutableStateFlow(LoadState(platforms.associate { it.id to PlatformSlice.Loading }))

    /** The running load of each platform and the account it is for. Touched only on the main thread. */
    private val inFlight = mutableMapOf<Platform, Flight>()

    /** The signed-in account of each platform, or null. */
    private val owners: Flow<Map<Platform, String?>> = accounts.states.map(::ownersOf).distinctUntilChanged()

    private val choices: Flow<Choices> =
        settings.settings
            .map { Choices(it.followingFilter.toFilter(), it.followingSort.toSort()) }
            .distinctUntilChanged()

    private val content: Flow<FollowingUiState> =
        combine(choices, loads, owners, lastLive.times) { choices, loads, owners, times ->
            uiState(choices, loads, owners).let { it.copy(offline = it.offline.withLastLive(times)) }
        }

    /** Emits nothing: it reloads on start, every [refreshInterval], and when an account changes. */
    private val autoRefresh: Flow<Nothing> =
        flow {
            owners.collectLatest { now ->
                cancelLoadsNotFor(now)
                while (true) {
                    refresh(platforms)
                    delay(refreshInterval)
                }
            }
        }

    /**
     * The screen state. The first value uses the default filter and sort; the stored ones replace them as soon as the
     * settings load. After the 5 s stop the cached value resets to that first value, so a collector that comes back
     * never starts from data an account change has made stale.
     */
    val state: StateFlow<FollowingUiState> =
        merge(content, autoRefresh).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS, replayExpirationMillis = 0),
            initialValue = uiState(Choices(), loads.value, ownersOf(accounts.states.value)),
        )

    /** Handles a user action. [FollowingIntent.Refresh] with no collector still loads once, shown on next subscribe. */
    fun onIntent(intent: FollowingIntent) {
        when (intent) {
            is FollowingIntent.SelectFilter -> save { it.copy(followingFilter = intent.filter.toSetting()) }
            is FollowingIntent.SelectSort -> save { it.copy(followingSort = intent.sort.toSetting()) }
            FollowingIntent.Refresh -> refresh(platforms)
            is FollowingIntent.Retry -> refresh(platforms.filter { it.id == intent.platform })
            is FollowingIntent.OfflineRowShown -> lastLive.request(intent.channel, viewModelScope)
        }
    }

    private fun ownersOf(states: Map<Platform, AccountState>): Map<Platform, String?> =
        platforms.associate { it.id to states[it.id]?.ownerId() }

    private fun save(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settings.update(transform) }
    }

    /** Cancels every load whose account is no longer the platform's signed-in account. */
    private fun cancelLoadsNotFor(now: Map<Platform, String?>) {
        for ((platform, flight) in inFlight.filter { (platform, flight) -> now[platform] != flight.owner }) {
            inFlight.remove(platform)
            flight.job.cancel()
            loads.update { it.finished(platform) }
        }
    }

    /** Starts a load for each signed-in platform in [targets] unless one is already running for the same account. */
    private fun refresh(targets: List<StreamingPlatform>) {
        val now = ownersOf(accounts.states.value)
        for (platform in targets) {
            val owner = now[platform.id] ?: continue
            val running = inFlight[platform.id]?.takeIf { it.job.isActive }
            if (running?.owner != owner) {
                running?.job?.cancel()
                start(platform, owner)
            }
        }
    }

    private fun start(
        platform: StreamingPlatform,
        owner: String,
    ) {
        // Lazy, so the job is registered before it runs and can recognise itself in inFlight.
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) { load(platform, owner) }
        inFlight[platform.id] = Flight(job, owner)
        job.start()
    }

    private suspend fun load(
        platform: StreamingPlatform,
        owner: String,
    ) {
        val id = platform.id
        val job = currentCoroutineContext().job
        loads.update { it.started(id, owner) }
        try {
            val result = withContext(dispatchers.io) { loadBoth(listOf(platform)) { it.followed() } }.getValue(id)
            // With nobody collecting, an account change doesn't cancel this job, so check before showing the result.
            if (isCurrent(id, job, owner)) loads.update { it.settled(id, owner, result) }
        } finally {
            if (inFlight[id]?.job === job) {
                inFlight.remove(id)
                loads.update { it.finished(id) }
            }
        }
    }

    private fun isCurrent(
        platform: Platform,
        job: Job,
        owner: String,
    ): Boolean = inFlight[platform]?.job === job && ownersOf(accounts.states.value)[platform] == owner

    private class Flight(
        val job: Job,
        val owner: String,
    )

    /**
     * Load results per platform. [owners] names the account each slice and error belongs to; [loading] maps each
     * platform being loaded to the account it is loading for.
     */
    private data class LoadState(
        val slices: Map<Platform, PlatformSlice<Followed>>,
        val owners: Map<Platform, String> = emptyMap(),
        val errors: Map<Platform, LurkError> = emptyMap(),
        val loading: Map<Platform, String> = emptyMap(),
    ) {
        /**
         * The same account's loaded items stay on screen, marked stale, while they reload. Anything else, including
         * another account's items, starts again from `Loading`.
         */
        fun started(
            platform: Platform,
            owner: String,
        ): LoadState {
            val current = slices.getValue(platform)
            return if (owners[platform] == owner && current is PlatformSlice.Loaded) {
                copy(
                    slices = slices + (platform to current.copy(stale = true)),
                    loading = loading + (platform to owner),
                )
            } else {
                copy(
                    slices = slices + (platform to PlatformSlice.Loading),
                    owners = owners + (platform to owner),
                    errors = errors - platform,
                    loading = loading + (platform to owner),
                )
            }
        }

        /** A failure over loaded items keeps them, stale, and records the error; anything else replaces the slice. */
        fun settled(
            platform: Platform,
            owner: String,
            result: PlatformSlice<Followed>,
        ): LoadState {
            val previous = slices.getValue(platform)
            val done = copy(owners = owners + (platform to owner), loading = loading - platform)
            return if (result is PlatformSlice.Failed && previous is PlatformSlice.Loaded &&
                owners[platform] == owner
            ) {
                done.copy(
                    slices = slices + (platform to previous.copy(stale = true)),
                    errors = errors + (platform to result.error),
                )
            } else {
                done.copy(slices = slices + (platform to result), errors = errors - platform)
            }
        }

        fun finished(platform: Platform): LoadState = copy(loading = loading - platform)
    }

    private data class Choices(
        val filter: PlatformFilter = PlatformFilter.ALL,
        val sort: FollowingSort = FollowingSort.VIEWERS,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L

        /** The account a platform's data belongs to. Expired keeps its account: the token is still the same user's. */
        fun AccountState.ownerId(): String? =
            when (this) {
                AccountState.SignedOut -> null
                is AccountState.SignedIn -> account.userId.ifBlank { account.login }
                is AccountState.Expired -> account.userId.ifBlank { account.login }
            }

        /**
         * Shows only data that belongs to each platform's current account: signed out is `NotSignedIn`, another
         * account's data is `Loading`, and only the current account's loads count as refreshing.
         */
        fun uiState(
            choices: Choices,
            loads: LoadState,
            owners: Map<Platform, String?>,
        ): FollowingUiState {
            fun current(platform: Platform): Boolean =
                owners[platform] != null && loads.owners[platform] == owners[platform]
            val shown =
                loads.slices
                    .mapValues { (platform, slice) ->
                        when {
                            owners[platform] == null -> PlatformSlice.NotSignedIn
                            current(platform) -> slice
                            else -> PlatformSlice.Loading
                        }
                    }.filterKeys(choices.filter::includes)
            val followed = shown.values.mapNotNull { (it as? PlatformSlice.Loaded)?.value }
            return FollowingUiState(
                filter = choices.filter,
                sort = choices.sort,
                live = followed.flatMap { it.live }.sortedFor(choices.sort),
                offline = followed.flatMap { it.offline }.sortedWith(byName),
                slices = shown,
                refreshing = loads.loading.any { (platform, owner) -> owners[platform] == owner },
                refreshErrors = loads.errors.filterKeys { current(it) && choices.filter.includes(it) },
            )
        }

        private val byName: Comparator<Channel> =
            compareBy<Channel, String>(String.CASE_INSENSITIVE_ORDER) { it.displayName }.thenBy { it.ref.platform }

        private val streamsByName: Comparator<Stream> =
            compareBy<Stream, String>(String.CASE_INSENSITIVE_ORDER) { it.displayName }.thenBy { it.channel.platform }

        /** Newest first; streams with no start time go last, by viewers. */
        private val byRecent: Comparator<Stream> =
            compareBy<Stream> { it.startedAt == null }
                .thenByDescending { it.startedAt }
                .thenByDescending { it.viewers }

        private fun List<Stream>.sortedFor(sort: FollowingSort): List<Stream> =
            when (sort) {
                FollowingSort.VIEWERS -> sortedByViewers()
                FollowingSort.RECENT -> sortedWith(byRecent)
                FollowingSort.NAME -> sortedWith(streamsByName)
            }

        private fun PlatformFilterSetting.toFilter(): PlatformFilter =
            when (this) {
                PlatformFilterSetting.ALL -> PlatformFilter.ALL
                PlatformFilterSetting.TWITCH -> PlatformFilter.TWITCH
                PlatformFilterSetting.KICK -> PlatformFilter.KICK
            }

        private fun PlatformFilter.toSetting(): PlatformFilterSetting =
            when (this) {
                PlatformFilter.ALL -> PlatformFilterSetting.ALL
                PlatformFilter.TWITCH -> PlatformFilterSetting.TWITCH
                PlatformFilter.KICK -> PlatformFilterSetting.KICK
            }

        private fun FollowingSortSetting.toSort(): FollowingSort =
            when (this) {
                FollowingSortSetting.VIEWERS -> FollowingSort.VIEWERS
                FollowingSortSetting.RECENT -> FollowingSort.RECENT
                FollowingSortSetting.NAME -> FollowingSort.NAME
            }

        private fun FollowingSort.toSetting(): FollowingSortSetting =
            when (this) {
                FollowingSort.VIEWERS -> FollowingSortSetting.VIEWERS
                FollowingSort.RECENT -> FollowingSortSetting.RECENT
                FollowingSort.NAME -> FollowingSortSetting.NAME
            }
    }
}
