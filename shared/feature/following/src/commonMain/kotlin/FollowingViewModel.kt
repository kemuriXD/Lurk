package io.github.kemurixd.lurk.feature.following

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kemurixd.lurk.auth.AccountState
import io.github.kemurixd.lurk.core.common.DispatcherProvider
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.model.Channel
import io.github.kemurixd.lurk.core.model.ChannelRef
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
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The Following list: every platform's followed channels, loaded concurrently and merged.
 *
 * While [state] has a collector, every signed-in platform reloads every [refreshInterval], and again whenever a
 * platform's account changes. Five seconds after the last collector leaves, the loop stops; the state stays, kept
 * current for account changes, so coming back shows the items at once and reloads quietly under them. Only a
 * reload the user asked for with [FollowingIntent.Refresh] shows a spinner ([FollowingUiState.manualRefresh]).
 * A platform that is still loading for the same account is never asked twice, whether the request comes from the
 * loop, [FollowingIntent.Refresh] or [FollowingIntent.Retry].
 *
 * Every load result is tagged with the account that produced it. Data from any other account is never shown: a
 * signed-out platform shows `NotSignedIn`, a switched one shows `Loading` until the new account's load lands, and a
 * load that finishes after its account left is dropped.
 *
 * Offline rows whose platform leaves out when they were last live get the time from [lastLive], only once the screen
 * reports them with [FollowingIntent.OfflineRowShown]. A row reported with [FollowingIntent.OfflineRowHidden]
 * before its turn costs no request.
 *
 * Call [onIntent] from the main thread.
 */
@OptIn(FlowPreview::class, ExperimentalForInheritanceCoroutinesApi::class)
class FollowingViewModel(
    private val platforms: List<StreamingPlatform>,
    private val accounts: AccountsState,
    private val settings: SettingsStore,
    private val dispatchers: DispatcherProvider,
    private val lastLive: LastLiveLoader,
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
            .map { Choices(it.followingFilter.toFilter(), it.followingSort.toSort(), it.followingOfflineCollapsed) }
            .distinctUntilChanged()

    /** The stored filter and sort and the looked-up times, as the latest values. Fed for the view model's life. */
    private val choicesNow = MutableStateFlow(Choices())
    private val timesNow = MutableStateFlow(emptyMap<ChannelRef, Instant>())

    /** How many collectors [state] has right now. */
    private val collectors = MutableStateFlow(0)

    /** What the screen shows right now, built from the live loads and the current accounts. */
    private fun render(): FollowingUiState =
        uiState(choicesNow.value, loads.value, ownersOf(accounts.states.value)).let {
            it.copy(offline = it.offline.withLastLive(timesNow.value))
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
     * The screen state. It is a function of the loads, the accounts, the stored choices and the looked-up times, all
     * read live, so a collector that comes back after the 5 s stop starts from the kept items, never from a skeleton,
     * and never from another account's data, even for the instant before anything has been recomputed. Only the
     * network loop stops with the last collector. The first value uses the default filter and sort until the stored
     * ones load.
     *
     * It is a hand-written StateFlow, because no operator gives one whose value is also current for an account that
     * changed with nobody collecting. Its contract: value never blocks and is always current, collect never
     * completes, and every collector sees distinct values in order. Inheriting StateFlow is opt-in in coroutines,
     * hence the OptIn on the class.
     */
    val state: StateFlow<FollowingUiState> =
        object : StateFlow<FollowingUiState> {
            override val replayCache: List<FollowingUiState> get() = listOf(value)

            override val value: FollowingUiState get() = render()

            override suspend fun collect(collector: FlowCollector<FollowingUiState>): Nothing {
                collectors.update { it + 1 }
                try {
                    combine(choicesNow, loads, accounts.states, timesNow) { _, _, _, _ -> render() }
                        .distinctUntilChanged()
                        .collect(collector)
                } finally {
                    collectors.update { it - 1 }
                }
                awaitCancellation()
            }
        }

    init {
        choices.onEach { choicesNow.value = it }.launchIn(viewModelScope)
        lastLive.times.onEach { timesNow.value = it }.launchIn(viewModelScope)
        // Runs the loop while somebody collects [state], and for 5 s after the last one leaves.
        collectors
            .map { it > 0 }
            .debounce { present -> if (present) 0L else STOP_TIMEOUT_MILLIS }
            .distinctUntilChanged()
            .onEach { present ->
                loopJob?.cancel()
                loopJob = if (present) autoRefresh.launchIn(viewModelScope) else null
            }.launchIn(viewModelScope)
    }

    private var loopJob: Job? = null

    /** Handles a user action. [FollowingIntent.Refresh] with no collector still loads once, shown on next subscribe. */
    fun onIntent(intent: FollowingIntent) {
        when (intent) {
            is FollowingIntent.SelectFilter -> {
                save { it.copy(followingFilter = intent.filter.toSetting()) }
            }

            is FollowingIntent.SelectSort -> {
                save { it.copy(followingSort = intent.sort.toSetting()) }
            }

            FollowingIntent.ToggleOffline -> {
                save { it.copy(followingOfflineCollapsed = !it.followingOfflineCollapsed) }
            }

            FollowingIntent.Refresh -> {
                refresh(platforms)
                // Only a reload the user asked for shows a spinner. With nothing loading there is nothing to show.
                loads.update { it.copy(manual = inFlight.values.any { flight -> flight.job.isActive }) }
            }

            is FollowingIntent.Retry -> {
                refresh(platforms.filter { it.id == intent.platform })
                markRetrying(intent.platform)
            }

            is FollowingIntent.OfflineRowShown -> {
                lastLive.request(intent.channel, viewModelScope)
            }

            is FollowingIntent.OfflineRowHidden -> {
                lastLive.release(intent.channel)
            }
        }
    }

    /** Flags [platform] as being asked again, if it has a failure on screen and a load is running for it. */
    private fun markRetrying(platform: Platform) {
        val running = inFlight[platform]?.job?.isActive == true
        loads.update {
            val failing = it.slices[platform] is PlatformSlice.Failed || platform in it.errors
            if (running && failing) it.copy(retrying = it.retrying + platform) else it
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
        val manual: Boolean = false,
        val retrying: Set<Platform> = emptySet(),
    ) {
        /**
         * The same account's loaded items stay on screen, marked stale, while they reload, and so does the same
         * account's failure, so a retry never swaps an error for a skeleton and back. Anything else, including another
         * account's items, starts again from `Loading`.
         */
        fun started(
            platform: Platform,
            owner: String,
        ): LoadState {
            val current = slices.getValue(platform)
            val sameAccount = owners[platform] == owner
            return if (sameAccount && current is PlatformSlice.Loaded) {
                copy(
                    slices = slices + (platform to current.copy(stale = true)),
                    loading = loading + (platform to owner),
                )
            } else if (sameAccount && current is PlatformSlice.Failed) {
                copy(loading = loading + (platform to owner))
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
            val done =
                copy(
                    owners = owners + (platform to owner),
                    loading = loading - platform,
                    retrying = retrying - platform,
                ).let { it.copy(manual = it.manual && it.loading.isNotEmpty()) }
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

        fun finished(platform: Platform): LoadState =
            (loading - platform).let { left ->
                copy(loading = left, manual = manual && left.isNotEmpty(), retrying = retrying - platform)
            }
    }

    private data class Choices(
        val filter: PlatformFilter = PlatformFilter.ALL,
        val sort: FollowingSort = FollowingSort.VIEWERS,
        val offlineCollapsed: Boolean = false,
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
            val refreshing = loads.loading.any { (platform, owner) -> owners[platform] == owner }
            return FollowingUiState(
                filter = choices.filter,
                sort = choices.sort,
                live = followed.flatMap { it.live }.sortedFor(choices.sort),
                offline = followed.flatMap { it.offline }.sortedWith(byName),
                slices = shown,
                refreshing = refreshing,
                manualRefresh = loads.manual && refreshing,
                retrying = retryingNow(choices, loads, owners),
                refreshErrors = loads.errors.filterKeys { current(it) && choices.filter.includes(it) },
                offlineCollapsed = choices.offlineCollapsed,
            )
        }

        /** Platforms the user asked again after a failure, still loading for the current account. Not a pull. */
        private fun retryingNow(
            choices: Choices,
            loads: LoadState,
            owners: Map<Platform, String?>,
        ): Set<Platform> =
            if (loads.manual) {
                emptySet()
            } else {
                loads.retrying
                    .filter { owners[it] != null && loads.loading[it] == owners[it] && choices.filter.includes(it) }
                    .toSet()
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
