package io.github.kemurixd.lurk.playback.hls

import io.github.kemurixd.lurk.playback.Variant

/** Reads the variants of an HLS master playlist. Lenient: unreadable input gives an empty list. */
object MasterPlaylist {
    private const val STREAM_INF = "#EXT-X-STREAM-INF:"
    private const val MEDIA = "#EXT-X-MEDIA:"
    private const val AUDIO_ONLY = "audio_only"

    /** Variants ordered by bandwidth, highest first. Relative URIs resolve against [baseUrl]. */
    fun variants(
        text: String,
        baseUrl: String,
    ): List<Variant> {
        val lines = text.lineSequence().map { it.trim() }.toList()
        val groupNames = HashMap<String, String>()
        for (line in lines) {
            if (!line.startsWith(MEDIA)) continue
            val a = parseAttributes(line.removePrefix(MEDIA))
            val group = a["GROUP-ID"]
            val name = a["NAME"]
            if (a["TYPE"] == "VIDEO" && group != null && name != null) groupNames.putIfAbsent(group, name)
        }
        val out = ArrayList<Variant>()
        var pending: Map<String, String>? = null
        for (line in lines) {
            when {
                line.startsWith(STREAM_INF) -> {
                    pending = parseAttributes(line.removePrefix(STREAM_INF))
                }

                isUri(line) -> {
                    pending?.let { variant(it, line, baseUrl, groupNames) }?.let(out::add)
                    pending = null
                }
            }
        }
        return out.sortedByDescending { it.bandwidth }
    }

    private fun isUri(line: String) = line.isNotEmpty() && !line.startsWith("#")

    private fun variant(
        a: Map<String, String>,
        uri: String,
        baseUrl: String,
        groupNames: Map<String, String>,
    ): Variant? {
        val bandwidth = a["BANDWIDTH"]?.toLongOrNull() ?: a["AVERAGE-BANDWIDTH"]?.toLongOrNull() ?: return null
        val size = a["RESOLUTION"]?.split('x', 'X')?.takeIf { it.size == 2 }
        val width = size?.get(0)?.toIntOrNull()
        val height = size?.get(1)?.toIntOrNull()
        val group = a["VIDEO"]
        val codecs =
            a["CODECS"]
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
        val audioOnly =
            height == null &&
                ((codecs.isNotEmpty() && codecs.all { it.startsWith("mp4a") }) || group == AUDIO_ONLY)
        val name =
            group?.let(groupNames::get)
                ?: a["IVS-NAME"]
                ?: a["STABLE-VARIANT-ID"]
                ?: if (height != null) {
                    "${height}p"
                } else if (audioOnly) {
                    AUDIO_ONLY
                } else {
                    "unknown"
                }
        return Variant(
            name = name,
            url = resolveUrl(baseUrl, uri),
            bandwidth = bandwidth,
            width = width,
            height = height,
            frameRate = a["FRAME-RATE"]?.toFloatOrNull(),
            audioOnly = audioOnly,
        )
    }

    private fun resolveUrl(
        base: String,
        ref: String,
    ): String {
        val schemeEnd = base.indexOf("://")
        val pathStart = base.indexOf('/', schemeEnd + 3).let { if (it < 0) base.length else it }
        return when {
            ref.contains("://") || schemeEnd < 0 -> {
                ref
            }

            ref.startsWith("//") -> {
                base.substring(0, schemeEnd + 1) + ref
            }

            ref.startsWith("/") -> {
                base.substring(0, pathStart) + ref
            }

            else -> {
                base.substring(0, pathStart) + "/" +
                    relativePath(base.substringBefore('#').substringBefore('?').substring(pathStart), ref)
            }
        }
    }

    private fun relativePath(
        basePath: String,
        ref: String,
    ): String {
        val segments =
            basePath
                .substringBeforeLast('/', "")
                .split('/')
                .filter { it.isNotEmpty() }
                .toMutableList()
        for (part in ref.split('/')) {
            when (part) {
                "." -> Unit
                ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.lastIndex)
                else -> segments.add(part)
            }
        }
        return segments.joinToString("/")
    }
}
