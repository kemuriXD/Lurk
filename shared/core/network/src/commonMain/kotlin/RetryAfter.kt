package io.github.kemurixd.lurk.core.network

import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.parseOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val MAX_RETRY_AFTER = 10.minutes

/**
 * Reads a `Retry-After` value: delta-seconds or an RFC 1123 date. The result is clamped to
 * 0 s..10 min; null when the value is missing or unparseable.
 */
fun parseRetryAfter(
    value: String?,
    now: Instant,
): Duration? {
    val trimmed = value?.trim() ?: return null
    val wait = trimmed.toLongOrNull()?.seconds ?: parseHttpDate(trimmed)?.let { it - now }
    return wait?.coerceIn(Duration.ZERO, MAX_RETRY_AFTER)
}

private fun parseHttpDate(value: String): Instant? =
    DateTimeComponents.Formats.RFC_1123
        .parseOrNull(value)
        ?.let { runCatching { it.toInstantUsingOffset() }.getOrNull() }
