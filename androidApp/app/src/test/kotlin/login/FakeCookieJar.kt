package io.github.kemurixd.lurk.login

import kotlinx.coroutines.CompletableDeferred

/** A [CookieJar] in memory. [gate], when set, holds every [clearAll] until it completes. */
internal class FakeCookieJar(
    private val events: MutableList<String>,
) : CookieJar {
    var header: String? = null
    var gate: CompletableDeferred<Unit>? = null
    val reads = mutableListOf<String>()

    override fun read(url: String): String? {
        reads += url
        return header
    }

    override suspend fun clearAll() {
        events += "clear started"
        gate?.await()
        header = null
        events += "clear done"
    }
}
