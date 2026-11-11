package io.github.kemurixd.lurk.core.network

import io.ktor.client.HttpClient
import org.koin.dsl.module
import kotlin.time.TimeSource

val networkModule =
    module {
        single<HttpClient> { createHttpClient() }
        single { Backoff() }
        single { HostRateLimiter(timeSource = TimeSource.Monotonic) }
    }
