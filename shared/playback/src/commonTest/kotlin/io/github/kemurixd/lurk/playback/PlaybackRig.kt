package io.github.kemurixd.lurk.playback

import io.github.kemurixd.lurk.core.common.DispatcherProvider
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.core.network.createHttpClient
import io.github.kemurixd.lurk.playback.ads.AdMarkers
import io.github.kemurixd.lurk.playback.proxy.ProxyChain
import io.github.kemurixd.lurk.playback.proxy.ProxyHealth
import io.github.kemurixd.lurk.playback.proxy.Route
import io.github.kemurixd.lurk.playback.proxy.RouteClients
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.io.IOException
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndIncrement
import kotlin.test.assertIs

const val MASTER = "https://usher.example/api/channel/hls/testchannel.m3u8"
const val MASTER_2 = "https://usher.example/api/channel/hls/testchannel.m3u8?renewed=1"
const val MEDIA = "https://video-weaver.example/v1/playlist/720p60.m3u8"
const val MEDIA_480 = "https://video-weaver.example/v1/playlist/480p30.m3u8"

val A = ProxyEndpoint.Http("127.0.0.1", 8080)
val B = ProxyEndpoint.Http("127.0.0.1", 8081)
val C = ProxyEndpoint.Http("127.0.0.1", 8082)
val TWITCH = ChannelRef(Platform.TWITCH, "testchannel")
val KICK = ChannelRef(Platform.KICK, "testchannel")
internal val MARKERS = AdMarkers(listOf("twitch-maf-ad"), listOf("twitch-stitched-ad"))

internal fun via(endpoint: ProxyEndpoint): Route = Route.ViaProxy(endpoint)

fun prefs(
    mode: ProxyMode,
    vararg endpoints: ProxyEndpoint,
): PlaybackPrefs = PlaybackPrefs(proxy = ProxySettings(mode, endpoints.toList()))

/** One playlist request as the network saw it. */
internal data class Request(
    val route: Route,
    val url: String,
    val headers: Map<String, String>,
    /** The request timeout the client applied, in milliseconds. */
    val timeoutMs: Long? = null,
)

sealed interface Reply {
    data class Text(
        val body: String,
    ) : Reply

    data class Status(
        val code: Int,
    ) : Reply

    data object Timeout : Reply

    data object Offline : Reply
}

/** A [PlaybackSource] that counts its grants. */
@OptIn(ExperimentalAtomicApi::class)
class FakePlaybackSource(
    override val platform: Platform = Platform.TWITCH,
    override val adPolicy: AdPolicy = AdPolicy.TWITCH_DETECT_AND_PROXY,
) : PlaybackSource {
    private val count = AtomicInt(0)
    val calls: Int get() = count.load()

    var onGrant: () -> Unit = {}
    var next: (call: Int) -> Outcome<PlaybackGrant> = { call ->
        Outcome.Success(PlaybackGrant(masterUrl = if (call == 0) MASTER else MASTER_2, headers = HEADERS))
    }

    override suspend fun grant(channel: ChannelRef): Outcome<PlaybackGrant> {
        onGrant()
        return next(count.fetchAndIncrement())
    }

    companion object {
        val HEADERS = mapOf("X-Test-Header" to "1")
    }
}

/**
 * [RouteClients] with one [MockEngine] per route. Every request is logged with the route it went through. Engines
 * run on [dispatcher], so handlers that suspend stay on the test's virtual clock.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class FakeRoutes(
    private val dispatcher: CoroutineDispatcher,
) : RouteClients {
    var master: String = fixture("twitch/master.m3u8")
    var media: String = fixture("twitch/media-live.m3u8")
    var reply: suspend (Route, String) -> Reply = { _, url -> Reply.Text(serve(url)) }

    private val log = AtomicReference<List<Request>>(emptyList())
    private val clients = AtomicReference<Map<Route, Pair<HttpClient, MockEngine>>>(emptyMap())

    val requests: List<Request> get() = log.load()

    fun urlsVia(route: Route): List<String> = requests.filter { it.route == route }.map { it.url }

    fun serve(url: String): String = if (url.startsWith("https://video-weaver.example/")) media else master

    override fun clientFor(route: Route): HttpClient {
        while (true) {
            val current = clients.load()
            current[route]?.let { return it.first }
            val engine = engine(route)
            val entry = createHttpClient(engine) to engine
            if (clients.compareAndSet(current, current + (route to entry))) return entry.first
            close(entry)
        }
    }

    /** Closes every client handed out so far, and its engine: a client does not own an engine passed to it. */
    fun close() {
        clients.exchange(emptyMap()).values.forEach(::close)
    }

    private fun close(entry: Pair<HttpClient, MockEngine>) {
        entry.first.close()
        entry.second.close()
    }

    private fun engine(route: Route): MockEngine {
        val config = MockEngineConfig()
        config.dispatcher = dispatcher
        config.addHandler { request ->
            val url = request.url.toString()
            val headers = request.headers.entries().associate { it.key to it.value.joinToString(",") }
            val timeoutMs = request.getCapabilityOrNull(HttpTimeoutCapability)?.requestTimeoutMillis
            append(Request(route, url, headers, timeoutMs))
            when (val r = reply(route, url)) {
                is Reply.Text -> respond(r.body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, M3U8))
                is Reply.Status -> respond("", HttpStatusCode.fromValue(r.code))
                Reply.Timeout -> throw HttpRequestTimeoutException(url, PROXY_TIMEOUT_MS)
                Reply.Offline -> throw IOException("connection refused")
            }
        }
        return MockEngine(config)
    }

    private fun append(request: Request) {
        while (true) {
            val current = log.load()
            if (log.compareAndSet(current, current + request)) return
        }
    }

    private companion object {
        const val M3U8 = "application/vnd.apple.mpegurl"
        const val PROXY_TIMEOUT_MS = 5_000L
    }
}

/** Serves the fixtures, but [fail] decides first; null lets the request through. */
internal fun Rig.script(fail: (Route, String) -> Reply?) {
    routes.reply = { route, url -> fail(route, url) ?: Reply.Text(routes.serve(url)) }
}

/** A resolver wired to fakes on the test's scheduler and virtual time. */
internal class Rig(
    private val test: TestScope,
    val source: FakePlaybackSource = FakePlaybackSource(),
) {
    val dispatcher = StandardTestDispatcher(test.testScheduler)
    val routes = FakeRoutes(dispatcher)
    val health = ProxyHealth(test.testScheduler.timeSource)
    private val dispatchers =
        object : DispatcherProvider {
            override val io: CoroutineDispatcher = dispatcher
            override val default: CoroutineDispatcher = dispatcher
            override val main: CoroutineDispatcher = dispatcher
        }

    init {
        // backgroundScope is cancelled when the test body ends: release the mock clients then.
        test.backgroundScope.coroutineContext.job
            .invokeOnCompletion { routes.close() }
    }

    val resolver =
        PlaylistResolver(
            sources = listOf(source),
            routes = routes,
            chain = ProxyChain(health, test.testScheduler.timeSource),
            markers = { MARKERS },
            dispatchers = dispatchers,
            timeSource = test.testScheduler.timeSource,
        )

    suspend fun resolve(
        prefs: PlaybackPrefs,
        channel: ChannelRef = TWITCH,
        scope: CoroutineScope = test.backgroundScope,
    ): ResolvedStream = assertIs<Outcome.Success<ResolvedStream>>(resolver.resolve(channel, prefs, scope)).value
}

/** Collects every value [flow] publishes from now on, without suspending the test. */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> TestScope.record(flow: kotlinx.coroutines.flow.Flow<T>): List<T> {
    val out = mutableListOf<T>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect { out += it } }
    return out
}
