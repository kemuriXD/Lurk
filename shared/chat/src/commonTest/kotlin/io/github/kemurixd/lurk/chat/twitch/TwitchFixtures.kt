package io.github.kemurixd.lurk.chat.twitch

import io.github.kemurixd.lurk.chat.fixture

/** The lines of `twitch/chat.irc`, a trailing `\r` trimmed. */
val chatIrcLines: List<String> by lazy {
    fixture("twitch/chat.irc").split('\n').map { it.trimEnd('\r') }.filter { it.isNotBlank() }
}

/** Line [number] of `twitch/chat.irc`, counted from 1 as in the fixture README. */
fun ircLine(number: Int): String = chatIrcLines[number - 1]

/** Parses line [number] of `twitch/chat.irc`, failing the test when it does not parse. */
fun ircMessage(number: Int): IrcMessage = requireNotNull(IrcParser.parse(ircLine(number))) { "line $number" }
