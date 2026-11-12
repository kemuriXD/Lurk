package io.github.kemurixd.lurk.chat

/**
 * [word] with leading and trailing punctuation trimmed, so `@name,` and `(name)` compare as `name`. Highlight and
 * block checks both split text on U+0020 and compare words through this.
 */
internal fun bareWord(word: String): String = word.trim { !it.isLetterOrDigit() && it != '_' }
