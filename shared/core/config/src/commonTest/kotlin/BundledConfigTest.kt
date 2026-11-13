package io.github.kemurixd.lurk.core.config

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Keeps the published config/remote.json decodable and valid on every CI run. */
class BundledConfigTest {
    @Test
    fun bundledConfigDecodesAndValidates() {
        val config = Json.decodeFromString<RemoteConfig>(BUNDLED_REMOTE_CONFIG)
        assertEquals(emptyList(), RemoteConfigValidator.errors(config))
    }

    @Test
    fun bundledConfigListsTheAndroidIdThenTheTvId() {
        val config = Json.decodeFromString<RemoteConfig>(BUNDLED_REMOTE_CONFIG)
        assertEquals(
            listOf("kd1unb4b3q4t58fwlpcbzcbnm76a8fp", "ue6666qo983tsx6so1t0vnawi233wa"),
            config.twitch.gqlDiscoveryClientIds,
        )
        assertTrue("gqlDiscoveryClientIds" in BUNDLED_REMOTE_CONFIG)
    }
}
