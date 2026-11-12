package io.github.kemurixd.lurk.chat

import io.github.kemurixd.lurk.core.model.ChatMessage
import io.github.kemurixd.lurk.core.model.Emote
import io.github.kemurixd.lurk.core.model.FfzEffect
import io.github.kemurixd.lurk.core.model.MessagePart
import io.github.kemurixd.lurk.core.model.MessagePart.EmotePart
import io.github.kemurixd.lurk.emotes.EmoteSnapshot

/**
 * Turns a [ChatMessage] into [MessagePart]s. Stateless, so one instance can serve every channel and thread.
 *
 * Native spans become emotes first. The text between them is split on U+0020 only: runs of spaces stay as text so
 * the parts spell the message back, and tabs or other whitespace stay inside words. Each word becomes, first match
 * wins, an emote, a cheer, a mention, a link, emoji, or text. Zero-width emotes stack onto the emote before them and
 * FrankerFaceZ modifiers fold into it; both look back past spaces only. No emote is ever dropped: a zero-width or
 * modifier emote with nothing to attach to renders on its own.
 */
class Tokenizer {
    /**
     * [cheerPrefixes] are lowercase cheermote prefixes, matched without regard to case and only when the message
     * carries bits. With [applyFfzEffects] off, modifier emotes render as ordinary emotes.
     */
    fun tokenize(
        message: ChatMessage,
        emotes: EmoteSnapshot,
        applyFfzEffects: Boolean = true,
        cheerPrefixes: Set<String> = DEFAULT_CHEER_PREFIXES,
    ): List<MessagePart> {
        val text = message.text
        val words = WordReader(message, emotes, cheerPrefixes, PartsBuilder(applyFfzEffects))
        var cursor = 0
        for (span in message.nativeEmotes) {
            // Parsers validate spans; one that is out of range or overlaps the last is read as plain text instead.
            if (span.start < cursor || span.start >= span.end || span.end > text.length) continue
            words.segment(cursor, span.start)
            words.parts.emote(span.emote, text.substring(span.start, span.end))
            cursor = span.end
        }
        words.segment(cursor, text.length)
        return words.parts.build()
    }

    private companion object {
        val DEFAULT_CHEER_PREFIXES = setOf("cheer")
    }
}

/** Collects parts for one message, merging adjacent text and attaching zero-width and modifier emotes. */
private class PartsBuilder(
    private val applyFfzEffects: Boolean,
) {
    private val parts = ArrayList<MessagePart>()

    /** Text not yet added to [parts]; always the tail of the message so far. */
    private val pending = StringBuilder()
    private var pendingHasWord = false

    fun spaces(
        text: String,
        start: Int,
        end: Int,
    ) {
        pending.append(text, start, end)
    }

    /** Appends text that is part of a word, so it contains no U+0020. */
    fun wordText(
        text: String,
        start: Int,
        end: Int,
    ) {
        if (start < end) {
            pending.append(text, start, end)
            pendingHasWord = true
        }
    }

    fun add(part: MessagePart) {
        flush()
        parts += part
    }

    fun emote(
        emote: Emote,
        source: String,
    ) {
        val target = attachTarget()
        when {
            target >= 0 && applyFfzEffects && emote.isModifier -> modify(target, emote)
            target >= 0 && emote.zeroWidth -> overlay(target, emote, source)
            else -> add(EmotePart(emote, source = source))
        }
    }

    fun build(): List<MessagePart> {
        flush()
        return parts
    }

    /** The index of the emote a zero-width or modifier emote would attach to, or -1 if text or nothing comes first. */
    private fun attachTarget(): Int = if (!pendingHasWord && parts.lastOrNull() is EmotePart) parts.lastIndex else -1

    private fun overlay(
        index: Int,
        emote: Emote,
        source: String,
    ) {
        val target = parts[index] as EmotePart
        parts[index] = target.copy(overlays = target.overlays + emote, source = "${target.source}$pending$source")
        pending.clear()
    }

    /** Folds a modifier's effects into the target; the modifier and the spaces before it leave the message. */
    private fun modify(
        index: Int,
        modifier: Emote,
    ) {
        val target = parts[index] as EmotePart
        val effects = target.effects + modifier.modifierEffects
        parts[index] = target.copy(effects = effects)
        pending.clear()
        if (FfzEffect.NO_SPACE in effects) trimSpacesBefore(index)
    }

    private fun trimSpacesBefore(index: Int) {
        val before = parts.getOrNull(index - 1) as? MessagePart.Text ?: return
        val trimmed = before.text.trimEnd(' ')
        if (trimmed.isEmpty()) parts.removeAt(index - 1) else parts[index - 1] = MessagePart.Text(trimmed)
    }

    private fun flush() {
        if (pending.isNotEmpty()) {
            parts += MessagePart.Text(pending.toString())
            pending.clear()
        }
        pendingHasWord = false
    }
}

/** Splits the message text into spaces and words and classifies each word. */
private class WordReader(
    message: ChatMessage,
    private val emotes: EmoteSnapshot,
    private val cheerPrefixes: Set<String>,
    val parts: PartsBuilder,
) {
    private val text = message.text
    private val senderId = message.sender?.id
    private val bits = message.bits

    fun segment(
        start: Int,
        end: Int,
    ) {
        var index = start
        while (index < end) {
            val isSpace = text[index] == ' '
            var next = index + 1
            while (next < end && (text[next] == ' ') == isSpace) next++
            if (isSpace) parts.spaces(text, index, next) else word(text.substring(index, next))
            index = next
        }
    }

    private fun word(word: String) {
        val emote = emotes.find(word, senderId)
        when {
            emote != null -> parts.emote(emote, word)
            cheer(word) -> Unit
            mention(word) -> Unit
            link(word) -> Unit
            else -> textAndEmoji(word, 0)
        }
    }

    private fun cheer(word: String): Boolean {
        if (bits <= 0) return false
        val cheer =
            cheerPrefixes.firstNotNullOfOrNull { prefix ->
                amountOf(word, prefix)?.let { MessagePart.Cheer(prefix, it, word) }
            }
        cheer?.let(parts::add)
        return cheer != null
    }

    private fun mention(word: String): Boolean {
        val isMention = word.length > 1 && word[0] == '@' && isLoginChar(word[1])
        if (isMention) {
            var end = 2
            while (end < word.length && isLoginChar(word[end])) end++
            parts.add(MessagePart.Mention(word.substring(1, end), word.substring(0, end)))
            textAndEmoji(word, end)
        }
        return isMention
    }

    private fun link(word: String): Boolean {
        val schemeEnd = schemeEnd(word)
        // Most words have neither a scheme nor a dot; skip them before any allocation.
        if (schemeEnd < 0 && '.' !in word) return false
        val end = linkEnd(word, if (schemeEnd > 0) word.indexOfFirst { it.isWhitespace() } else -1)
        val source = word.substring(0, end)
        val isLink = if (schemeEnd > 0) end > schemeEnd else BARE_LINK.matches(source)
        if (isLink) {
            parts.add(MessagePart.Link(if (schemeEnd > 0) source else "https://$source", source))
            textAndEmoji(word, end)
        }
        return isLink
    }

    /** Adds [word] from [start] on as text, cutting out each emoji run as its own part. */
    private fun textAndEmoji(
        word: String,
        start: Int,
    ) {
        var textStart = start
        var index = start
        while (index < word.length) {
            val runEnd = emojiRunEnd(word, index, word.length)
            if (runEnd > index) {
                parts.wordText(word, textStart, index)
                parts.add(MessagePart.Emoji(word.substring(index, runEnd)))
                textStart = runEnd
                index = runEnd
            } else {
                index = nextCodePoint(word, index, word.length)
            }
        }
        parts.wordText(word, textStart, word.length)
    }
}

private val BARE_LINK = Regex("""(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\.)+[A-Za-z]{2,24}(?:/\S*)?""")

private const val TRAILING_PUNCTUATION = ".,!?:;"

private fun isLoginChar(c: Char): Boolean = c == '_' || c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9'

/** The bits in `<prefix><digits>` (prefix in any case), or null when [word] is not that shape or the amount is 0. */
private fun amountOf(
    word: String,
    prefix: String,
): Int? {
    val start = prefix.length
    val shaped =
        word.length > start &&
            word.startsWith(prefix, ignoreCase = true) &&
            (start until word.length).all { word[it] in '0'..'9' }
    return if (shaped) word.substring(start).toIntOrNull()?.takeIf { it > 0 } else null
}

/** The index just past `http://` or `https://` (any case), or -1. */
private fun schemeEnd(word: String): Int =
    when {
        word.startsWith("https://", ignoreCase = true) -> "https://".length
        word.startsWith("http://", ignoreCase = true) -> "http://".length
        else -> -1
    }

/**
 * Where a link candidate ends: at [stop] (or the end when it is -1), minus trailing punctuation. A closing paren is
 * trimmed only while the candidate has more `)` than `(`, so `https://x.com/a_(b)` keeps its paren.
 */
private fun linkEnd(
    word: String,
    stop: Int,
): Int {
    var end = if (stop < 0) word.length else stop
    while (end > 0) {
        val last = word[end - 1]
        val trim = last in TRAILING_PUNCTUATION || (last == ')' && hasUnbalancedClose(word, end))
        if (!trim) break
        end--
    }
    return end
}

private fun hasUnbalancedClose(
    word: String,
    end: Int,
): Boolean {
    var depth = 0
    for (index in 0 until end) {
        when (word[index]) {
            '(' -> depth++
            ')' -> depth--
        }
    }
    return depth < 0
}
