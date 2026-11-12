package io.github.kemurixd.lurk.auth

import io.github.kemurixd.lurk.core.model.Platform
import kotlin.test.Test
import kotlin.test.assertEquals

class LoginSpecTest {
    @Test
    fun kickCookieIsUrlDecoded() {
        assertEquals("kat_a|b", LoginSpec.KICK.tokenFrom("kat_a%7Cb"))
    }

    @Test
    fun kickDecodesMultiByteSequencesAndLeavesBrokenEscapes() {
        assertEquals("a é", LoginSpec.KICK.tokenFrom("a%20%C3%A9"))
        assertEquals("a%zz%4", LoginSpec.KICK.tokenFrom("a%zz%4"))
    }

    @Test
    fun twitchCookieIsKeptAsIs() {
        assertEquals("abc%7Cdef", LoginSpec.TWITCH.tokenFrom("abc%7Cdef"))
    }

    @Test
    fun constantsMatchTheLoginPages() {
        assertEquals(
            LoginSpec(Platform.TWITCH, "https://www.twitch.tv/login", ".twitch.tv", "auth-token"),
            LoginSpec.TWITCH,
        )
        assertEquals(
            LoginSpec(Platform.KICK, "https://kick.com/login", "kick.com", "session_token"),
            LoginSpec.KICK,
        )
        assertEquals(LoginSpec.KICK, LoginSpec.forPlatform(Platform.KICK))
        assertEquals(LoginSpec.TWITCH, LoginSpec.forPlatform(Platform.TWITCH))
    }
}
