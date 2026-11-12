package io.github.kemurixd.lurk.core.config

/** Sanity checks a config before the app trusts it. A config with any error is never used. */
object RemoteConfigValidator {
    private val sha256Hex = Regex("^[0-9a-f]{64}$")
    private val channelPlaceholders = listOf("{chatroomId}", "{channelId}")
    private const val HTTPS = "https://"

    fun errors(c: RemoteConfig): List<String> =
        buildList {
            if (c.schemaVersion != SUPPORTED_SCHEMA) {
                add("schemaVersion ${c.schemaVersion} is not the supported $SUPPORTED_SCHEMA")
            }
            if (c.twitch.gqlClientId.isBlank()) add("twitch.gqlClientId is blank")
            c.twitch.persistedQueries.forEach { (name, hash) ->
                if (!sha256Hex.matches(hash)) add("twitch.persistedQueries.$name is not a 64-character hex hash")
            }
            if (c.twitch.adCueClasses.isEmpty()) add("twitch.adCueClasses is empty")
            requireHttps("twitch.recentMessagesUrl", c.twitch.recentMessagesUrl)
            requireHttps("kick.apiBase", c.kick.apiBase)
            requireHttps("kick.webApiBase", c.kick.webApiBase)
            requireHttps("kick.searchApiBase", c.kick.searchApiBase)
            c.kick.publicChannelTemplates.forEach { template ->
                if (channelPlaceholders.none { it in template }) {
                    add("kick.publicChannelTemplates entry '$template' has no {chatroomId} or {channelId}")
                }
            }
        }

    private fun MutableList<String>.requireHttps(
        field: String,
        value: String,
    ) {
        if (!value.startsWith(HTTPS)) add("$field does not start with $HTTPS")
    }
}
