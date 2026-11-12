package io.github.kemurixd.lurk.smoke

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SmokeConfigTest {
    private fun fileWith(text: String): Path = Files.createTempFile("smoke", ".properties").also { it.writeText(text) }

    @Test
    fun missingFileSkipsSignedIn() {
        val config = SmokeConfig.load(Path.of("this-file-does-not-exist.properties"))
        assertNull(config.twitchToken)
        assertNull(config.kickToken)
        assertNull(config.twitchChannel)
        assertNull(config.kickChannel)
        assertNull(config.kickFollowTest)
        assertFalse(config.hasSignedIn)
    }

    @Test
    fun missingChannelSkipsChannelChecks() {
        val config = SmokeConfig.load(fileWith("twitch.authToken=aaa\nkick.sessionToken=bbb\n"))
        assertNull(config.twitchChannel)
        assertNull(config.kickChannel)
        assertTrue(config.hasSignedIn)
        val lines = mutableListOf<String>()
        SmokeReport(emptyList(), lines::add).skipNoChannel("twitch.grant.anon")
        assertEquals(listOf("SKIP twitch.grant.anon no channel"), lines)
    }

    @Test
    fun readsEveryKey() {
        val config =
            SmokeConfig.load(
                fileWith(
                    "twitch.authToken=t1\nkick.sessionToken=k1\ntwitch.channel= Streamer1 \n" +
                        "kick.channel=streamer2\nkick.followTest=streamer3\n",
                ),
            )
        assertEquals("t1", config.twitchToken)
        assertEquals("k1", config.kickToken)
        assertEquals("streamer1", config.twitchChannel)
        assertEquals("streamer2", config.kickChannel)
        assertEquals("streamer3", config.kickFollowTest)
    }

    @Test
    fun blankValuesCountAsUnset() {
        val config = SmokeConfig.load(fileWith("twitch.authToken=\nkick.sessionToken=   \ntwitch.channel=\n"))
        assertNull(config.twitchToken)
        assertNull(config.kickToken)
        assertNull(config.twitchChannel)
    }

    @Test
    fun secretsCoverTheDecodedKickToken() {
        val config = SmokeConfig.load(fileWith("kick.sessionToken=abc%7Cdef\ntwitch.authToken=tw\n"))
        assertTrue("abc%7Cdef" in config.secrets)
        assertTrue("abc|def" in config.secrets)
        assertTrue("tw" in config.secrets)
    }

    @Test
    fun toStringHidesTokens() {
        val config = SmokeConfig.load(fileWith("twitch.authToken=sekrit1\nkick.sessionToken=sekrit2\n"))
        val text = config.toString()
        assertFalse("sekrit1" in text)
        assertFalse("sekrit2" in text)
    }
}
