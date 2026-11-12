package io.github.kemurixd.lurk.platform.twitch.assets

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.LurkException
import io.github.kemurixd.lurk.core.model.Cheermote
import io.github.kemurixd.lurk.core.model.CheermoteTier
import io.github.kemurixd.lurk.platform.twitch.discovery.discoveryRig
import io.github.kemurixd.lurk.platform.twitch.discovery.reply
import io.github.kemurixd.lurk.platform.twitch.fixture
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TwitchCheermoteSourceTest {
    private fun globalTier(
        bits: Int,
        color: String?,
    ) = CheermoteTier(bits, color, "https://cdn.example/actions/globalcheer/dark/animated/$bits/2.gif")

    @Test
    fun cheermoteTiersSorted() =
        runTest {
            val r = discoveryRig { reply(fixture("gql/cheermotes.json")) }
            val cheermotes = TwitchCheermoteSource(r.gql).load("1000001")

            assertEquals(
                listOf(
                    Cheermote(
                        "GlobalCheer",
                        listOf(globalTier(1, "#979797"), globalTier(100, "#9c3ee8"), globalTier(1000, "#1db2a5")),
                    ),
                    Cheermote(
                        "Streamer1Cheer",
                        listOf(
                            CheermoteTier(1, "#979797", "https://cdn.example/partner/1/1/dark/animated/2.gif"),
                            CheermoteTier(100, "#9c3ee8", "https://cdn.example/partner/1/100/dark/animated/2.gif"),
                        ),
                    ),
                ),
                cheermotes,
            )
        }

    @Test
    fun missingUserKeepsGlobalCheermotes() =
        runTest {
            val r = discoveryRig { reply(fixture("gql/cheermotes-unknown.json")) }

            assertEquals(listOf("GlobalCheer"), TwitchCheermoteSource(r.gql).load("1000001").map { it.prefix })
        }

    @Test
    fun badItemSkipped() =
        runTest {
            val r = discoveryRig { reply(fixture("gql/cheermotes-bad-item.json")) }

            assertEquals(
                listOf(Cheermote("GlobalCheer", listOf(globalTier(1, "#979797")))),
                TwitchCheermoteSource(r.gql).load("1000001"),
            )
        }

    @Test
    fun serverErrorThrows() =
        runTest {
            val r = discoveryRig { reply("{}", HttpStatusCode.InternalServerError) }

            val e = assertFailsWith<LurkException> { TwitchCheermoteSource(r.gql).load("1000001") }
            assertEquals(LurkError.Http(500), e.error)
        }
}
