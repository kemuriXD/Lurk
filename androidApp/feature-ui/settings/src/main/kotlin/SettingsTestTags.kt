package io.github.kemurixd.lurk.featureui.settings

import io.github.kemurixd.lurk.core.model.Platform

/** Test tags on the Settings screen, shared with tests in other modules. */
object SettingsTestTags {
    const val ACCOUNTS = "settings_accounts"
    const val CONFIRM_SIGN_OUT = "settings_confirm_sign_out"

    /** The row's button: Sign in, Sign in again or Sign out. */
    fun action(platform: Platform): String = "settings_account_action_${platform.id}"
}
