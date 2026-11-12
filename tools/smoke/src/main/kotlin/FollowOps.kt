package io.github.kemurixd.lurk.smoke

import io.github.kemurixd.lurk.core.model.Channel
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Followed

/** What the follow test needs from a platform. A failed call gives null or false. */
internal interface FollowOps {
    suspend fun channel(ref: ChannelRef): Channel?

    suspend fun followed(): Followed?

    suspend fun setFollow(
        channel: Channel,
        follow: Boolean,
    ): Boolean
}

/**
 * The follow list stops at 20 pages, and the page size is not exposed. A list this long may be cut off, so the test
 * leaves the follow alone rather than risk unfollowing a channel it could not see. Assumes Kick pages hold at least
 * 5 channels (20-page cap, see `FOLLOWED_MAX_PAGES` in KickFollows.kt).
 */
internal const val FOLLOW_LIST_LIMIT = 100

private const val CHECK = "kick.followTest"

private fun Followed.contains(ref: ChannelRef) = live.any { it.channel == ref } || offline.any { it.ref == ref }

/** Follows, checks, unfollows, checks. Prints `restored=true` only when the channel ends up not followed. */
internal suspend fun runFollowTest(
    ref: ChannelRef,
    ops: FollowOps,
    report: SmokeReport,
) {
    val channel = ops.channel(ref)
    val before = ops.followed()
    when {
        channel == null || before == null -> {
            report.fail(CHECK, "lookup failed")
        }

        before.size >= FOLLOW_LIST_LIMIT -> {
            report.skip(CHECK, "follow list incomplete")
        }

        // The round trip acts on the server's channel, so compare that one, not the configured slug.
        before.contains(channel.ref) -> {
            report.skip(CHECK, "already following")
        }

        else -> {
            report.pass(CHECK, "restored=${roundTrip(channel, ops, before.size)}")
        }
    }
}

/**
 * Follows, then unfollows only when the list shows exactly one more entry that is this channel. Anything else (a
 * capped list, a changed list) leaves the follow in place and reports `restored=false`, so an existing follow can
 * never be lost.
 */
private suspend fun roundTrip(
    channel: Channel,
    ops: FollowOps,
    sizeBefore: Int,
): Boolean {
    if (!ops.setFollow(channel, true)) return false
    val after = ops.followed()
    val safe = after != null && after.contains(channel.ref) && after.size == sizeBefore + 1
    var restored = false
    if (safe) {
        try {
            restored = ops.setFollow(channel, false)
        } finally {
            restored = restored && ops.followed()?.contains(channel.ref) == false
        }
    }
    return restored
}

private val Followed.size get() = live.size + offline.size
