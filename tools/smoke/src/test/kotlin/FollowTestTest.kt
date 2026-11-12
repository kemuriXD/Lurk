package io.github.kemurixd.lurk.smoke

import io.github.kemurixd.lurk.core.model.Channel
import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Followed
import io.github.kemurixd.lurk.core.model.Platform
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private fun ref(login: String) = ChannelRef(Platform.KICK, login)

private fun channel(login: String) = Channel(ref(login), "1", "1", "Name", null, null, null, null, null, null)

private class FakeOps(
    private val server: Channel?,
    private val initial: List<Channel> = emptyList(),
    private val throwAfterFollow: Boolean = false,
) : FollowOps {
    val calls = mutableListOf<String>()
    private val following = initial.toMutableList()

    override suspend fun channel(ref: ChannelRef) = server

    override suspend fun followed(): Followed? {
        if (throwAfterFollow && calls.contains("follow")) error("lookup exploded")
        return Followed(emptyList(), following.toList())
    }

    override suspend fun setFollow(
        channel: Channel,
        follow: Boolean,
    ): Boolean {
        calls += if (follow) "follow" else "unfollow"
        if (follow) following += channel else following.removeAll { it.ref == channel.ref }
        return true
    }
}

class FollowTestTest {
    private val lines = mutableListOf<String>()
    private val report = SmokeReport(emptyList(), lines::add)

    @Test
    fun happyPathRestores() =
        runBlocking {
            val ops = FakeOps(channel("streamer1"))
            runFollowTest(ref("streamer1"), ops, report)
            assertEquals(listOf("follow", "unfollow"), ops.calls)
            assertEquals(listOf("PASS kick.followTest restored=true"), lines)
        }

    @Test
    fun alreadyFollowingComparesTheServerChannel() =
        runBlocking {
            // The configured slug differs from the server's slug, which is the one that is followed.
            val ops = FakeOps(channel("real-slug"), initial = listOf(channel("real-slug")))
            runFollowTest(ref("Configured"), ops, report)
            assertTrue(ops.calls.isEmpty())
            assertEquals(listOf("SKIP kick.followTest already following"), lines)
        }

    @Test
    fun longFollowListIsLeftAlone() =
        runBlocking {
            val many = (1..FOLLOW_LIST_LIMIT).map { channel("c$it") }
            val ops = FakeOps(channel("streamer1"), initial = many)
            runFollowTest(ref("streamer1"), ops, report)
            assertTrue(ops.calls.isEmpty())
            assertEquals(listOf("SKIP kick.followTest follow list incomplete"), lines)
        }

    @Test
    fun unfollowStillRunsWhenAVerifyStepThrows() =
        runBlocking {
            val ops = FakeOps(channel("streamer1"), throwAfterFollow = true)
            assertFailsWith<IllegalStateException> { runFollowTest(ref("streamer1"), ops, report) }
            assertEquals(listOf("follow", "unfollow"), ops.calls)
        }

    @Test
    fun lookupFailureHasNoRestoredText() =
        runBlocking {
            runFollowTest(ref("streamer1"), FakeOps(null), report)
            assertEquals(listOf("FAIL kick.followTest lookup failed"), lines)
        }
}
