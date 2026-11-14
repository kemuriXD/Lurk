package io.github.kemurixd.lurk.login

/**
 * The value of cookie [name] in a `Cookie` header as `CookieManager.getCookie` gives it (`a=1; b=2`). The name must
 * match exactly; the value is split off at the first `=` only (values may contain `=`) and trimmed. Null when the
 * cookie is missing or empty. The first non-empty match wins.
 */
fun cookieValue(
    cookieHeader: String?,
    name: String,
): String? =
    cookieHeader
        ?.splitToSequence(';')
        ?.mapNotNull { pair ->
            val split = pair.indexOf('=')
            if (split < 0 || pair.substring(0, split).trim() != name) null else pair.substring(split + 1).trim()
        }?.firstOrNull { it.isNotEmpty() }
