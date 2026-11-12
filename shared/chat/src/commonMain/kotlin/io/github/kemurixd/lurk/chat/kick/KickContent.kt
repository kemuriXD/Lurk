package io.github.kemurixd.lurk.chat.kick

import io.github.kemurixd.lurk.core.model.Emote
import io.github.kemurixd.lurk.core.model.EmoteImage
import io.github.kemurixd.lurk.core.model.EmoteProvider
import io.github.kemurixd.lurk.core.model.EmoteScope
import io.github.kemurixd.lurk.core.model.NativeEmoteSpan

/** Turns Kick's `[emote:<id>:<name>]` markup into plain text plus emote spans. */
object KickContent {
    private val token = Regex("""\[emote:(\d+):([^\[\]\s]+)]""")

    /**
     * Replaces each token with its name and returns the new text with a span per emote (UTF-16 indices, exclusive
     * end). A token that does not match the pattern stays in the text as written.
     */
    fun split(content: String): Pair<String, List<NativeEmoteSpan>> {
        val spans = mutableListOf<NativeEmoteSpan>()
        val out = StringBuilder()
        var last = 0
        for (match in token.findAll(content)) {
            out.append(content, last, match.range.first)
            val (id, name) = match.destructured
            spans += NativeEmoteSpan(out.length, out.length + name.length, emote(id, name))
            out.append(name)
            last = match.range.last + 1
        }
        out.append(content, last, content.length)
        return out.toString() to spans
    }

    private fun emote(
        id: String,
        name: String,
    ): Emote =
        Emote(
            id = id,
            name = name,
            provider = EmoteProvider.KICK,
            scope = EmoteScope.CHANNEL,
            images = listOf(EmoteImage(1, "https://files.kick.com/emotes/$id/fullsize")),
        )
}
