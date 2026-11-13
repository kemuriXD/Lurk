package io.github.kemurixd.lurk.login

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CookieValueTest {
    @Test
    fun cookieValueExactName() {
        assertEquals("abc", cookieValue("auth-token2=x; auth-token=abc", "auth-token"))
    }

    @Test
    fun cookieValueIgnoresLongerAndShorterNames() {
        assertNull(cookieValue("xauth-token=a; auth-token2=b; auth-toke=c", "auth-token"))
    }

    @Test
    fun cookieValueMissingIsNull() {
        assertNull(cookieValue("a=1; b=2", "auth-token"))
        assertNull(cookieValue(null, "auth-token"))
        assertNull(cookieValue("", "auth-token"))
        assertNull(cookieValue("auth-token", "auth-token"))
    }

    @Test
    fun cookieValueEmptyIsNull() {
        assertNull(cookieValue("auth-token=; b=2", "auth-token"))
        assertNull(cookieValue("a=1; auth-token=   ", "auth-token"))
    }

    @Test
    fun cookieValueTrimsWhitespace() {
        assertEquals("abc", cookieValue("a=1;   auth-token =  abc  ;b=2", "auth-token"))
    }

    @Test
    fun cookieValueKeepsEqualsSignsInTheValue() {
        assertEquals("ab%3D=c=", cookieValue("session_token=ab%3D=c=; a=1", "session_token"))
    }
}
