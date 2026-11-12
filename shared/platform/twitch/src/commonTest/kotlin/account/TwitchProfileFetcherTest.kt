package io.github.kemurixd.lurk.platform.twitch.account

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Account
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.platform.twitch.discovery.discoveryRig
import io.github.kemurixd.lurk.platform.twitch.discovery.reply
import io.github.kemurixd.lurk.platform.twitch.fixture
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TwitchProfileFetcherTest {
    @Test
    fun profileMapsAccount() =
        runTest {
            val r = discoveryRig { reply(fixture("gql/current-user.json")) }
            val result = TwitchProfileFetcher(r.gql).profile("some-token")

            val expected = Account(Platform.TWITCH, "1000099", "viewer1", "Viewer One", "https://cdn.example/a/v1.png")
            assertEquals(Outcome.Success(expected), result)
            assertEquals("OAuth some-token", r.seen[0].headers["Authorization"])
        }

    @Test
    fun nullCurrentUserIsAuthExpired() =
        runTest {
            val r = discoveryRig { reply(fixture("gql/current-user-null.json")) }

            assertEquals(Outcome.Failure(LurkError.AuthExpired), TwitchProfileFetcher(r.gql).profile("some-token"))
        }

    @Test
    fun platformIsTwitch() =
        runTest {
            val r = discoveryRig { reply(fixture("gql/current-user.json")) }

            assertEquals(Platform.TWITCH, TwitchProfileFetcher(r.gql).platform)
        }
}
