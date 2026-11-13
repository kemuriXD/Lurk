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
import io.github.kemurixd.lurk.core.storage.FollowingSort
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
 * While [state] has a collector, every platform reloads every [refreshInterval], and again whenever a platform is
 * signed in or out. Five seconds after the last collector leaves, the loop stops. A platform that is still loading is
 * never asked twice, whether the request comes from the loop, [FollowingIntent.Refresh] or [FollowingIntent.Retry].
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
    private val slices =
        MutableStateFlow<Map<Platform, PlatformSlice<Followed>>>(
            platforms.associate { platform ->
                platform.id to if (accounts.signedIn(platform.id)) PlatformSlice.Loading else PlatformSlice.NotSignedIn
            },
        )
    private val refreshErrors = MutableStateFlow<Map<Platform, LurkError>>(emptyMap())
    private val loading = MutableStateFlow<Set<Platform>>(emptySet())

    /** The job loading each platform. Touched only on the main thread. */
    private val inFlight = mutableMapOf<Platform, Job>()

    private val choices: Flow<Choices> =
        settings.settings
            .map { Choices(it.followingFilter.toFilter(), it.followingSort) }
            .distinctUntilChanged()

    private val content: Flow<FollowingUiState> =
        combine(choices, slices, refreshErrors, loading) { choices, slices, errors, loading ->
            uiState(choices, slices, errors, loading.isNotEmpty())
        }

    /** Emits nothing: it reloads on start, every [refreshInterval], and when the signed-in platforms change. */
    private val autoRefresh: Flow<Nothing> =
        flow {
            accounts.states
                .map { platforms.filter { accounts.signedIn(it.id) }.map { it.id }.toSet() }
                .distinctUntilChanged()
                .collectLatest {
                    while (true) {
                        refresh(platforms)
                        delay(refreshInterval)
                    }
                }
        }

    val state: StateFlow<FollowingUiState> =
        merge(content, autoRefresh).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = uiState(Choices(), slices.value, emptyMap(), refreshing = false),
        )

    fun onIntent(intent: FollowingIntent) {
        when (intent) {
            is FollowingIntent.SelectFilter -> {
                save { it.copy(followingFilter = intent.filter.toSetting()) }
            }

            is FollowingIntent.SelectSort -> {
                save { it.copy(followingSort = intent.sort) }
            }

            FollowingIntent.Refresh -> {
                refresh(platforms)
            }

            is FollowingIntent.Retry -> {
                refresh(platforms.filter { it.id == intent.platform })
            }
        }
    }

    private fun save(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settings.update(transform) }
    }

    /** Starts a load for each of [targets] that isn't already loading. */
    private fun refresh(targets: List<StreamingPlatform>) {
        for (platform in targets) {
            if (inFlight[platform.id]?.isActive == true) continue
            inFlight[platform.id] =
                viewModelScope.launch {
                    loading.update { it + platform.id }
                    try {
                        load(platform)
                    } finally {
                        loading.update { it - platform.id }
                    }
                }
        }
    }

    private suspend fun load(platform: StreamingPlatform) {
        val id = platform.id
        if (!accounts.signedIn(id)) {
            settle(id, PlatformSlice.NotSignedIn, error = null)
            return
        }
        slices.update { it + (id to it.getValue(id).reloading()) }
        val result = withContext(dispatchers.io) { loadBoth(listOf(platform)) { it.followed() } }.getValue(id)
        val previous = slices.value.getValue(id)
        if (result is PlatformSlice.Failed && previous is PlatformSlice.Loaded) {
            settle(id, previous.copy(stale = true), error = result.error)
        } else {
            settle(id, result, error = null)
        }
    }

    private fun settle(
        platform: Platform,
        slice: PlatformSlice<Followed>,
        error: LurkError?,
    ) {
        slices.update { it + (platform to slice) }
        refreshErrors.update { if (error == null) it - platform else it + (platform to error) }
    }

    private data class Choices(
        val filter: PlatformFilter = PlatformFilter.ALL,
        val sort: FollowingSort = FollowingSort.VIEWERS,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L

        /** Loaded items stay on screen, marked stale, while they reload; anything else shows as loading. */
        fun PlatformSlice<Followed>.reloading(): PlatformSlice<Followed> =
            if (this is PlatformSlice.Loaded) copy(stale = true) else PlatformSlice.Loading

        fun uiState(
            choices: Choices,
            slices: Map<Platform, PlatformSlice<Followed>>,
            errors: Map<Platform, LurkError>,
            refreshing: Boolean,
        ): FollowingUiState {
            val shown = slices.filterKeys(choices.filter::includes)
            val followed = shown.values.mapNotNull { (it as? PlatformSlice.Loaded)?.value }
            return FollowingUiState(
                filter = choices.filter,
                sort = choices.sort,
                live = followed.flatMap { it.live }.sortedFor(choices.sort),
                offline = followed.flatMap { it.offline }.sortedWith(byName),
                slices = shown,
                refreshing = refreshing,
                refreshErrors = errors.filterKeys(choices.filter::includes),
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
    }
}
