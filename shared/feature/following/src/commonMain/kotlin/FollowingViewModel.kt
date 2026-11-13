package io.github.kemurixd.lurk.feature.following

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import io.github.kemurixd.lurk.feature.common.PlatformFilter
import io.github.kemurixd.lurk.feature.common.PlatformSlice
import io.github.kemurixd.lurk.feature.common.loadBoth
import io.github.kemurixd.lurk.feature.common.sortedByViewers
import io.github.kemurixd.lurk.platform.api.StreamingPlatform
import kotlinx.coroutines.Job
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The Following list: every platform's followed channels, loaded concurrently and merged.
 *
 * While [state] has a collector, every signed-in platform reloads every [refreshInterval], and again whenever a
 * platform is signed in. Five seconds after the last collector leaves, the loop stops. A platform that is still
 * loading is never asked twice, whether the request comes from the loop, [FollowingIntent.Refresh] or
 * [FollowingIntent.Retry].
 *
 * Sign-in comes straight from [AccountsState]: a signed-out platform shows `NotSignedIn` at once, its load is
 * cancelled, and a result that lands after the sign-out is dropped.
 *
 * Call [onIntent] from the main thread.
 */
class FollowingViewModel(
    private val platforms: List<StreamingPlatform>,
    private val accounts: AccountsState,
    private val settings: SettingsStore,
    private val dispatchers: DispatcherProvider,
    private val refreshInterval: Duration = 60.seconds,
) : ViewModel() {
    /** Every platform's load results in one value, so the UI never sees a slice and its error out of step. */
    private val loads = MutableStateFlow(LoadState(platforms.associate { it.id to PlatformSlice.Loading }))

    /** The job loading each platform. Touched only on the main thread. */
    private val inFlight = mutableMapOf<Platform, Job>()

    private val signedIn: Flow<Set<Platform>> = accounts.states.map { signedInNow() }.distinctUntilChanged()

    private val choices: Flow<Choices> =
        settings.settings
            .map { Choices(it.followingFilter.toFilter(), it.followingSort.toSort()) }
            .distinctUntilChanged()

    private val content: Flow<FollowingUiState> = combine(choices, loads, signedIn, ::uiState)

    /** Emits nothing: it reloads on start, every [refreshInterval], and when the signed-in platforms change. */
    private val autoRefresh: Flow<Nothing> =
        flow {
            signedIn.collectLatest { now ->
                forgetSignedOut(now)
                while (true) {
                    refresh(platforms)
                    delay(refreshInterval)
                }
            }
        }

    /**
     * The screen state. The first value uses the default filter and sort; the stored ones replace them as soon as the
     * settings load.
     */
    val state: StateFlow<FollowingUiState> =
        merge(content, autoRefresh).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = uiState(Choices(), loads.value, signedInNow()),
        )

    /** Handles a user action. [FollowingIntent.Refresh] with no collector still loads once, shown on next subscribe. */
    fun onIntent(intent: FollowingIntent) {
        when (intent) {
            is FollowingIntent.SelectFilter -> save { it.copy(followingFilter = intent.filter.toSetting()) }
            is FollowingIntent.SelectSort -> save { it.copy(followingSort = intent.sort.toSetting()) }
            FollowingIntent.Refresh -> refresh(platforms)
            is FollowingIntent.Retry -> refresh(platforms.filter { it.id == intent.platform })
        }
    }

    private fun signedInNow(): Set<Platform> = platforms.map { it.id }.filter(accounts::signedIn).toSet()

    private fun save(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settings.update(transform) }
    }

    /** Cancels the loads of platforms that left [now] and forgets their results, so a new account starts clean. */
    private fun forgetSignedOut(now: Set<Platform>) {
        for (platform in platforms.map { it.id }.filterNot(now::contains)) {
            inFlight.remove(platform)?.cancel()
            loads.update { it.forget(platform) }
        }
    }

    /** Starts a load for each signed-in platform in [targets] that isn't already loading. */
    private fun refresh(targets: List<StreamingPlatform>) {
        for (platform in targets) {
            if (!accounts.signedIn(platform.id) || inFlight[platform.id]?.isActive == true) continue
            inFlight[platform.id] = viewModelScope.launch { load(platform) }
        }
    }

    private suspend fun load(platform: StreamingPlatform) {
        val id = platform.id
        loads.update { it.started(id) }
        try {
            val result = withContext(dispatchers.io) { loadBoth(listOf(platform)) { it.followed() } }.getValue(id)
            // With nobody collecting, a sign-out doesn't cancel this job, so check again before showing the result.
            if (accounts.signedIn(id)) loads.update { it.settled(id, result) }
        } finally {
            loads.update { it.copy(loading = it.loading - id) }
        }
    }

    private data class LoadState(
        val slices: Map<Platform, PlatformSlice<Followed>>,
        val errors: Map<Platform, LurkError> = emptyMap(),
        val loading: Set<Platform> = emptySet(),
    ) {
        /** Loaded items stay on screen, marked stale, while they reload; anything else shows as loading. */
        fun started(platform: Platform): LoadState {
            val current = slices.getValue(platform)
            val reloading = if (current is PlatformSlice.Loaded) current.copy(stale = true) else PlatformSlice.Loading
            return copy(slices = slices + (platform to reloading), loading = loading + platform)
        }

        /** A failure over loaded items keeps them, stale, and records the error; anything else replaces the slice. */
        fun settled(
            platform: Platform,
            result: PlatformSlice<Followed>,
        ): LoadState {
            val previous = slices.getValue(platform)
            return if (result is PlatformSlice.Failed && previous is PlatformSlice.Loaded) {
                copy(
                    slices = slices + (platform to previous.copy(stale = true)),
                    errors = errors + (platform to result.error),
                    loading = loading - platform,
                )
            } else {
                copy(slices = slices + (platform to result), errors = errors - platform, loading = loading - platform)
            }
        }

        fun forget(platform: Platform): LoadState =
            copy(
                slices = slices + (platform to PlatformSlice.Loading),
                errors = errors - platform,
                loading = loading - platform,
            )
    }

    private data class Choices(
        val filter: PlatformFilter = PlatformFilter.ALL,
        val sort: FollowingSort = FollowingSort.VIEWERS,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L

        /** Signed-out platforms show `NotSignedIn` whatever their last load said, and never count as refreshing. */
        fun uiState(
            choices: Choices,
            loads: LoadState,
            signedIn: Set<Platform>,
        ): FollowingUiState {
            val shown =
                loads.slices
                    .mapValues { (platform, slice) -> if (platform in signedIn) slice else PlatformSlice.NotSignedIn }
                    .filterKeys(choices.filter::includes)
            val followed = shown.values.mapNotNull { (it as? PlatformSlice.Loaded)?.value }
            return FollowingUiState(
                filter = choices.filter,
                sort = choices.sort,
                live = followed.flatMap { it.live }.sortedFor(choices.sort),
                offline = followed.flatMap { it.offline }.sortedWith(byName),
                slices = shown,
                refreshing = loads.loading.any { it in signedIn },
                refreshErrors = loads.errors.filterKeys { it in signedIn && choices.filter.includes(it) },
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
