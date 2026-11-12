package io.github.kemurixd.lurk.platform.kick.assets

import io.github.kemurixd.lurk.core.model.Emote
import io.github.kemurixd.lurk.core.model.EmoteImage
import io.github.kemurixd.lurk.core.model.EmoteProvider
import io.github.kemurixd.lurk.core.model.EmoteScope
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.emotes.EmoteChannel
import io.github.kemurixd.lurk.platform.kick.api.Rig
import io.github.kemurixd.lurk.platform.kick.api.json
import io.github.kemurixd.lurk.platform.kick.api.mockEngine
import io.github.kemurixd.lurk.platform.kick.fixture
import io.ktor.client.request.HttpRequestData
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class KickEmoteSourceTest {
    private fun emote(
        id: Long,
        name: String,
        scope: EmoteScope,
    ) = Emote(
        id = id.toString(),
        name = name,
        provider = EmoteProvider.KICK,
        scope = scope,
        images = listOf(EmoteImage(1, "https://files.kick.com/emotes/$id/fullsize")),
    )

    @Test
    fun emoteSetsMapped() =
        runTest {
            val seen = mutableListOf<HttpRequestData>()
            val api =
                Rig(
                    mockEngine {
                        seen += it
                        json(fixture("assets/emotes.json"))
                    },
                ).api

            val emotes = KickEmoteSource(api).load(EmoteChannel(Platform.KICK, "a/b"))

            assertEquals("/emotes/a%2Fb", seen.single().url.encodedPath)
            assertEquals("site.kick.example", seen.single().url.host)
            assertEquals(
                listOf(
                    emote(1000001, "chanWave", EmoteScope.CHANNEL),
                    emote(1000002, "chanHype", EmoteScope.CHANNEL),
                    emote(1000003, "chanSub", EmoteScope.CHANNEL),
                    emote(1000011, "globalOne", EmoteScope.GLOBAL),
                    emote(1000012, "globalTwo", EmoteScope.GLOBAL),
                    emote(1000021, "emojiOne", EmoteScope.GLOBAL),
                    emote(1000022, "emojiTwo", EmoteScope.GLOBAL),
                ),
                emotes,
            )
        }
}
