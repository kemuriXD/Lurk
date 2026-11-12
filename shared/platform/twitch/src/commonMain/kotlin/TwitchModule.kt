package io.github.kemurixd.lurk.platform.twitch

import io.github.kemurixd.lurk.auth.ProfileFetcher
import io.github.kemurixd.lurk.core.config.RemoteConfigRepository
import io.github.kemurixd.lurk.core.network.ChallengeSolver
import io.github.kemurixd.lurk.platform.api.StreamingPlatform
import io.github.kemurixd.lurk.platform.twitch.account.TwitchProfileFetcher
import io.github.kemurixd.lurk.platform.twitch.gql.DeviceId
import io.github.kemurixd.lurk.platform.twitch.gql.GqlClient
import io.github.kemurixd.lurk.platform.twitch.playback.TwitchPlaybackSource
import io.github.kemurixd.lurk.playback.PlaybackSource
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Binds Twitch. Needs `HttpClient`, `WebSocketConnector`, `SecureStore`, `DispatcherProvider`,
 * `RemoteConfigRepository`,
 * the `SessionRepository` from `authModule` and the `appScope` coroutine scope. A [ChallengeSolver] is optional.
 * The platform, its [PlaybackSource] and its [ProfileFetcher] are also bound under those interfaces, so `getAll()`
 * finds them next to Kick's.
 */
val twitchModule: Module =
    module {
        single { DeviceId(get()) }
        single {
            GqlClient(get(), get<RemoteConfigRepository>().config, get(), get(), getOrNull<ChallengeSolver>())
        }
        single { TwitchPlaybackSource(get(), get(), get<RemoteConfigRepository>().config) } bind PlaybackSource::class
        single { TwitchProfileFetcher(get()) } bind ProfileFetcher::class
        single {
            TwitchPlatform(
                gql = get(),
                sessions = get(),
                http = get(),
                connector = get(),
                config = get<RemoteConfigRepository>().config,
                scope = get(named("appScope")),
                playback = get<TwitchPlaybackSource>(),
            )
        } bind StreamingPlatform::class
    }
