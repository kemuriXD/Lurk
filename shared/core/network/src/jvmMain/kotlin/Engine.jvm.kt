package io.github.kemurixd.lurk.core.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

actual fun defaultEngine(): HttpClientEngine = OkHttp.create()
