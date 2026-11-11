package io.github.kemurixd.lurk.core.config

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/** Keeps the published config/remote.json decodable and valid on every CI run. */
class BundledConfigTest {
    @Test
    fun bundledConfigDecodesAndValidates() {
        val config = Json.decodeFromString<RemoteConfig>(BUNDLED_REMOTE_CONFIG)
        assertEquals(emptyList(), RemoteConfigValidator.errors(config))
    }
}
