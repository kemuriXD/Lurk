package io.github.kemurixd.lurk.platform.twitch.discovery

import io.github.kemurixd.lurk.core.model.Channel
import io.github.kemurixd.lurk.core.model.Followed
import io.github.kemurixd.lurk.core.model.Page
import io.github.kemurixd.lurk.core.model.SearchResults
import io.github.kemurixd.lurk.core.model.Stream
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** How long one preview URL stays the same. The CDN caches live previews for as long (`max-age=300`). */
private val PREVIEW_REFRESH = 5.minutes

/**
 * This stream with a cache key on its preview URL: `?t=<start, epoch seconds>-<five-minute bucket of now>`.
 *
 * Twitch's preview URL is the same for every broadcast of a login. Before a new broadcast's first preview exists, and
 * whenever the channel is offline, the CDN redirects it to a grey placeholder, and an image cache keyed on the bare
 * URL would keep showing that placeholder after the real preview appears. The key changes with each broadcast and
 * every five minutes, so a cached placeholder or stale frame lives at most one bucket. The CDN ignores the query, as
 * it does for Twitch's own site.
 */
internal fun Stream.withPreviewKey(now: Instant): Stream {
    val url = thumbnailUrl ?: return this
    val bucket = now.epochSeconds / PREVIEW_REFRESH.inWholeSeconds
    val broadcast = startedAt?.epochSeconds ?: 0L
    val separator = if ('?' in url) '&' else '?'
    return copy(thumbnailUrl = "$url${separator}t=$broadcast-$bucket")
}

internal fun Page<Stream>.withPreviewKeys(now: Instant): Page<Stream> =
    copy(
        items =
            items.map {
                it.withPreviewKey(now)
            },
    )

internal fun Channel.withPreviewKey(now: Instant): Channel = copy(live = live?.withPreviewKey(now))

internal fun Followed.withPreviewKeys(now: Instant): Followed = copy(live = live.map { it.withPreviewKey(now) })

internal fun SearchResults.withPreviewKeys(now: Instant): SearchResults =
    copy(channels = channels.map { it.withPreviewKey(now) })
