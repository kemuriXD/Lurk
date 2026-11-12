package io.github.kemurixd.lurk.platform.twitch.assets

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.LurkException
import io.github.kemurixd.lurk.core.model.Emote
import io.github.kemurixd.lurk.core.model.EmoteImage
import io.github.kemurixd.lurk.core.model.EmoteProvider
import io.github.kemurixd.lurk.core.model.EmoteScope
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.emotes.EmoteChannel
import io.github.kemurixd.lurk.platform.twitch.discovery.discoveryRig
import io.github.kemurixd.lurk.platform.twitch.discovery.reply
import io.github.kemurixd.lurk.platform.twitch.fixture
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TwitchEmoteSourceTest {
    private val channel = EmoteChannel(Platform.TWITCH, "1000001")

    private fun emote(
        id: String,
        name: String,
    ) = Emote(
        id = id,
        name = name,
        provider = EmoteProvider.TWITCH,
        scope = EmoteScope.CHANNEL,
        images =
            listOf(
                EmoteImage(1, "https://static-cdn.jtvnw.net/emoticons/v2/$id/default/dark/1.0"),
                EmoteImage(2, "https://static-cdn.jtvnw.net/emoticons/v2/$id/default/dark/2.0"),
                EmoteImage(3, "https://static-cdn.jtvnw.net/emoticons/v2/$id/default/dark/3.0"),
            ),
    )

    @Test
    fun channelEmotesMapped() =
        runTest {
            val r = discoveryRig { reply(fixture("gql/emotes.json")) }
            val emotes = TwitchEmoteSource(r.gql).load(channel)

            assertEquals(
                listOf(
                    emote("emotesv2_stand1", "streamer1OK"),
                    emote("emotesv2_stand2", "streamer1Hype"),
                    emote("emotesv2_stand3", "streamer1Rook"),
                ),
                emotes,
            )
            val sent = r.operation()
            assertEquals("1000001", sent["variables"]!!.jsonObject["id"]!!.jsonPrimitive.content)
            assertNull(r.seen.single().headers["Authorization"])
        }

    @Test
    fun missingUserIsEmpty() =
        runTest {
            val r = discoveryRig { reply(fixture("gql/emotes-unknown.json")) }

            assertEquals(emptyList(), TwitchEmoteSource(r.gql).load(channel))
        }

    @Test
    fun notFoundStatusIsEmpty() =
        runTest {
            val r = discoveryRig { reply("{}", HttpStatusCode.NotFound) }

            assertEquals(emptyList(), TwitchEmoteSource(r.gql).load(channel))
        }

    @Test
    fun badItemSkipped() =
        runTest {
            val r = discoveryRig { reply(fixture("gql/emotes-bad-item.json")) }

            assertEquals(listOf(emote("emotesv2_stand1", "streamer1OK")), TwitchEmoteSource(r.gql).load(channel))
        }

    @Test
    fun serverErrorThrows() =
        runTest {
            val r = discoveryRig { reply("{}", HttpStatusCode.InternalServerError) }

            val e = assertFailsWith<LurkException> { TwitchEmoteSource(r.gql).load(channel) }
            assertEquals(LurkError.Http(500), e.error)
        }
}
