package io.github.kemurixd.lurk.chat

private const val SURROGATE_OFFSET = 0x10000
private const val HIGH_SURROGATE_BASE = 0xD800
private const val LOW_SURROGATE_BASE = 0xDC00
private const val SURROGATE_SHIFT = 10

/** The lowest code point any emoji range starts at; everything below it (minus surrogates) is skipped quickly. */
private const val FIRST_EMOJI = 0x2600
private const val SYMBOLS_END = 0x27BF
private const val PICTOGRAPHS_START = 0x1F300
private const val PICTOGRAPHS_END = 0x1FAFF
private const val REGIONAL_START = 0x1F1E6
private const val REGIONAL_END = 0x1F1FF
private const val ZWJ = 0x200D
private const val VARIATION_SELECTOR = 0xFE0F

/** Reads the code point at [index], joining a valid surrogate pair; a lone surrogate is returned as itself. */
private fun codePointAt(
    text: CharSequence,
    index: Int,
    end: Int,
): Int {
    val high = text[index]
    if (high.isHighSurrogate() && index + 1 < end) {
        val low = text[index + 1]
        if (low.isLowSurrogate()) {
            return ((high.code - HIGH_SURROGATE_BASE) shl SURROGATE_SHIFT) + (low.code - LOW_SURROGATE_BASE) +
                SURROGATE_OFFSET
        }
    }
    return high.code
}

private fun charCount(codePoint: Int): Int = if (codePoint >= SURROGATE_OFFSET) 2 else 1

/** Code points that start or continue an emoji run. Skin tones (1F3FB–1F3FF) sit inside the pictograph range. */
private fun isEmojiBase(codePoint: Int): Boolean =
    codePoint in FIRST_EMOJI..SYMBOLS_END ||
        codePoint in PICTOGRAPHS_START..PICTOGRAPHS_END ||
        codePoint in REGIONAL_START..REGIONAL_END

/** Code points that only continue a run: the zero-width joiner and the emoji variation selector. */
private fun isJoiner(codePoint: Int): Boolean = codePoint == ZWJ || codePoint == VARIATION_SELECTOR

/**
 * If an emoji run starts at [start], returns the index just past it, else [start]. A run is one or more emoji code
 * points with any joiners, variation selectors and skin tones between and after them, so a ZWJ family, a flag pair or
 * a toned thumbs-up is a single run. Stops at [end].
 */
internal fun emojiRunEnd(
    text: CharSequence,
    start: Int,
    end: Int,
): Int {
    val first = text[start]
    // Cheap pre-check: every emoji here is in the BMP symbol block or behind a high surrogate.
    val candidate = first.code in FIRST_EMOJI..SYMBOLS_END || first.isHighSurrogate()
    var codePoint = if (candidate) codePointAt(text, start, end) else 0
    if (!isEmojiBase(codePoint)) return start
    var index = start
    while (index < end && (isEmojiBase(codePoint) || isJoiner(codePoint))) {
        index += charCount(codePoint)
        if (index < end) codePoint = codePointAt(text, index, end)
    }
    return index
}

/** The index of the code point after the one at [index], stepping over a whole surrogate pair. */
internal fun nextCodePoint(
    text: CharSequence,
    index: Int,
    end: Int,
): Int = index + charCount(codePointAt(text, index, end))
