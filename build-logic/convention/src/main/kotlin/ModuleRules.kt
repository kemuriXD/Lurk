/** The module dependency rules from the architecture spec, expressed over Gradle project paths. */
object ModuleRules {
    private const val CORE = ":shared:core"
    private const val TWITCH = ":shared:platform:twitch"
    private const val KICK = ":shared:platform:kick"
    private const val FEATURE = ":shared:feature"
    private const val DESIGNSYSTEM = ":androidApp:designsystem"
    private val androidUi = listOf(":androidApp:feature-ui", DESIGNSYSTEM, ":androidApp:chat-renderer")

    /** Returns null when `from` may depend on `to`, otherwise a short message naming the broken rule. */
    fun violations(
        from: String,
        to: String,
    ): String? =
        (coreRule(from, to) ?: platformRule(from, to) ?: featureRule(from, to) ?: uiRule(from, to))
            ?.let { "$from -> $to: $it" }

    private fun coreRule(
        from: String,
        to: String,
    ) = "core modules may depend only on other core modules".takeIf { from.within(CORE) && !to.within(CORE) }

    private fun platformRule(
        from: String,
        to: String,
    ) = "twitch and kick platform modules must not depend on each other".takeIf {
        (from.within(TWITCH) && to.within(KICK)) || (from.within(KICK) && to.within(TWITCH))
    }

    private fun featureRule(
        from: String,
        to: String,
    ) = "feature modules must not depend on a concrete platform module".takeIf {
        from.within(FEATURE) && (to.within(TWITCH) || to.within(KICK))
    }

    private fun uiRule(
        from: String,
        to: String,
    ) = "Android UI modules may depend only on feature modules and designsystem".takeIf {
        androidUi.any { from.within(it) } && !to.within(FEATURE) && !to.within(DESIGNSYSTEM)
    }

    private fun String.within(prefix: String) = this == prefix || startsWith("$prefix:")
}
