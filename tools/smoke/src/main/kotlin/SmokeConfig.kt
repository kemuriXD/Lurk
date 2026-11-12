package io.github.kemurixd.lurk.smoke

import io.github.kemurixd.lurk.auth.LoginSpec
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/**
 * What the private smoke file holds. Every key is optional; there are no default channels, so a check that needs a
 * channel is skipped when its key is unset. [toString] hides the tokens.
 */
class SmokeConfig(
    val twitchToken: String? = null,
    val kickToken: String? = null,
    val twitchChannel: String? = null,
    val kickChannel: String? = null,
    val kickFollowTest: String? = null,
) {
    val hasSignedIn: Boolean get() = twitchToken != null || kickToken != null

    /** Every secret in the file, including the decoded Kick token, so a report can drop them literally. */
    val secrets: List<String>
        get() =
            listOfNotNull(twitchToken, kickToken, kickToken?.let(LoginSpec.KICK::tokenFrom)).distinct()

    override fun toString(): String =
        "SmokeConfig(twitchToken=${mask(twitchToken)}, kickToken=${mask(kickToken)}, " +
            "twitchChannel=${twitchChannel != null}, kickChannel=${kickChannel != null}, " +
            "kickFollowTest=${kickFollowTest != null})"

    private fun mask(value: String?) = if (value == null) "unset" else "set"

    companion object {
        /** Reads the file at [path]. A missing or unreadable file gives an empty config. */
        fun load(path: Path): SmokeConfig {
            if (!Files.isRegularFile(path)) return SmokeConfig()
            val props = Properties()
            Files.newBufferedReader(path).use(props::load)

            fun value(key: String) = props.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }

            return SmokeConfig(
                twitchToken = value("twitch.authToken"),
                kickToken = value("kick.sessionToken"),
                twitchChannel = value("twitch.channel")?.lowercase(),
                kickChannel = value("kick.channel")?.lowercase(),
                kickFollowTest = value("kick.followTest")?.lowercase(),
            )
        }
    }
}
