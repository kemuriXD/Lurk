package io.github.kemurixd.lurk.designsystem.component

private const val THOUSAND = 1_000L
private const val MILLION = 1_000_000L
private const val TENTHS = 10L

// Below this, thousands still round to a value under 1000K.
private const val ROUNDS_TO_MILLION = 999_950L

/** Compact viewer count: `999`, `12.4K`, `1.5M`. Locale-free so the output is the same everywhere. */
fun formatViewers(count: Long): String =
    when {
        count < THOUSAND -> count.toString()
        count < ROUNDS_TO_MILLION -> compact(count, THOUSAND, "K")
        else -> compact(count, MILLION, "M")
    }

private fun compact(
    count: Long,
    unit: Long,
    suffix: String,
): String {
    val tenths = (count * TENTHS + unit / 2) / unit
    val whole = tenths / TENTHS
    val fraction = tenths % TENTHS
    return if (fraction == 0L) "$whole$suffix" else "$whole.$fraction$suffix"
}
