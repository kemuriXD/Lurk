package io.github.kemurixd.lurk.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class LurkErrorTest {
    @Test
    fun retryableTable() {
        val expected =
            mapOf(
                LurkError.Offline to true,
                LurkError.Timeout to true,
                LurkError.ProxyFailed to true,
                LurkError.Unexpected to true,
                LurkError.Http(500) to true,
                LurkError.Http(503) to true,
                LurkError.RateLimited(null) to true,
                LurkError.RateLimited(30.seconds) to true,
                LurkError.Http(404) to false,
                LurkError.IntegrityRequired to false,
                LurkError.SchemaChanged to false,
                LurkError.Blocked to false,
                LurkError.AuthExpired to false,
                LurkError.NotFound to false,
                LurkError.NotSignedIn to false,
                LurkError.Unsupported to false,
            )
        for ((error, retryable) in expected) assertEquals(retryable, error.isRetryable, "$error")
    }
}
