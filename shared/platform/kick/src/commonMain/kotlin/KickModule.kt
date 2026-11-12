package io.github.kemurixd.lurk.platform.kick

import io.github.kemurixd.lurk.auth.ProfileFetcher
import io.github.kemurixd.lurk.core.config.RemoteConfigRepository
import io.github.kemurixd.lurk.core.network.ChallengeSolver
import io.github.kemurixd.lurk.platform.api.StreamingPlatform
import io.github.kemurixd.lurk.platform.kick.account.KickProfileFetcher
import io.github.kemurixd.lurk.platform.kick.api.CloudflareGate
import io.github.kemurixd.lurk.platform.kick.api.KickApi
import io.github.kemurixd.lurk.platform.kick.playback.KickPlaybackSource
import io.github.kemurixd.lurk.platform.kick.realtime.RealtimeBootstrap
import io.github.kemurixd.lurk.playback.PlaybackSource
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Binds Kick. Needs `HttpClient`, the `CookiesStorage` that client uses (so a solved Cloudflare challenge writes
 * cookies the client really sends), `WebSocketConnector`, `RemoteConfigRepository` and the `SessionRepository` from
 * `authModule`. A [ChallengeSolver] is optional. The platform, its [PlaybackSource] and its [ProfileFetcher] are also
 * bound under those interfaces, so `getAll()` finds them next to Twitch's.
 */
val kickModule: Module =
    module {
        single { CloudflareGate(getOrNull<ChallengeSolver>(), get()) }
        single { KickApi(get(), get<RemoteConfigRepository>().config, get()) }
        single { RealtimeBootstrap(get(), get<RemoteConfigRepository>().config) }
        single { KickPlaybackSource(get(), get()) } bind PlaybackSource::class
        single { KickProfileFetcher(get()) } bind ProfileFetcher::class
        single {
            KickPlatform(
                api = get(),
                sessions = get(),
                bootstrap = get(),
                connector = get(),
                playback = get<KickPlaybackSource>(),
            )
        } bind StreamingPlatform::class
    }
