package io.github.kemurixd.lurk.feature.common

import io.github.kemurixd.lurk.core.model.Stream

private val byViewers: Comparator<Stream> =
    compareByDescending<Stream> { it.viewers }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.displayName }
        .thenBy { it.channel.platform }

/** Most viewers first; ties by display name (case-insensitive), then by platform. */
fun List<Stream>.sortedByViewers(): List<Stream> = sortedWith(byViewers)
