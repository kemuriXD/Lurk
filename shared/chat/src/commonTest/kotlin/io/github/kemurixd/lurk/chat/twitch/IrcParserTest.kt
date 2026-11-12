package io.github.kemurixd.lurk.chat.twitch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IrcParserTest {
    @Test
    fun parsesTagsPrefixCommandAndTrailing() {
        val msg = ircMessage(5)
        assertEquals("10000000-0000-4000-8000-00000000a3eb", msg.tags["id"])
        assertEquals("user_5701", msg.tags["display-name"])
        assertEquals("", msg.tags["emotes"])
        assertEquals("user_5701!user_5701@user_5701.tmi.twitch.tv", msg.prefix)
        assertEquals("PRIVMSG", msg.command)
        assertEquals(listOf("#user_5700", "good game everyone"), msg.params)
    }

    @Test
    fun unescapesTagValues() {
        val msg = IrcParser.parse("@display-name=a\\sb;x=c\\:d\\\\e\\rf\\ng;y=end\\ :u!u@u PRIVMSG #c :hi")
        assertEquals("a b", msg?.tags?.get("display-name"))
        assertEquals("c;d\\e\rf\ng", msg?.tags?.get("x"))
        assertEquals("end", msg?.tags?.get("y"))
    }

    @Test
    fun tagWithoutEqualsHasEmptyValue() {
        val msg = IrcParser.parse("@flag;a=1 :tmi.twitch.tv ROOMSTATE #c")
        assertEquals(mapOf("flag" to "", "a" to "1"), msg?.tags)
    }

    @Test
    fun lineWithoutTagsOrPrefix() {
        val msg = IrcParser.parse("PING :tmi.twitch.tv")
        assertEquals(IrcMessage(emptyMap(), null, "PING", listOf("tmi.twitch.tv")), msg)
    }

    @Test
    fun middleParamsAndCarriageReturn() {
        val msg = IrcParser.parse(":host 353 me = #chan :me\r\n")
        assertEquals(listOf("me", "=", "#chan", "me"), msg?.params)
        assertEquals("353", msg?.command)
    }

    @Test
    fun blankAndGarbageLinesAreNull() {
        listOf(
            "",
            "   ",
            "garbage",
            "@a=b",
            "@a=b ",
            ":prefix",
            ":prefix ",
            "@a=b :prefix",
            "12",
            "PRIV-MSG #c",
        ).forEach {
            assertNull(IrcParser.parse(it), "'$it' should not parse")
        }
    }
}
