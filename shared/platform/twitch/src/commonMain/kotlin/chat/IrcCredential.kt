package io.github.kemurixd.lurk.platform.twitch.chat

/**
 * What a signed-in IRC connection logs in with: the OAuth token for `PASS` and the account login for `NICK`. Twitch
 * only treats the connection as the user's when the nick is their login. [toString] hides both.
 */
class IrcCredential(
    val token: String,
    login: String,
) {
    val login: String = login.lowercase()

    override fun toString(): String = "IrcCredential(<redacted>)"
}
