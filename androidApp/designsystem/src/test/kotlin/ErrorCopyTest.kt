package io.github.kemurixd.lurk.designsystem

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.kemurixd.lurk.core.common.LurkError
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ErrorCopyTest {
    private val everyError: List<LurkError> =
        listOf(
            LurkError.Offline,
            LurkError.Timeout,
            LurkError.AuthExpired,
            LurkError.Blocked,
            LurkError.IntegrityRequired,
            LurkError.ProxyFailed,
            LurkError.SchemaChanged,
            LurkError.NotFound,
            LurkError.NotSignedIn,
            LurkError.Unsupported,
            LurkError.Unexpected,
            LurkError.Http(500),
            LurkError.RateLimited(null),
            LurkError.RateLimited(30.seconds),
        )

    @Test
    fun errorCopyCoversEveryLurkError() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (error in everyError) {
            val res = error.messageRes()
            assertNotEquals("$error", 0, res)
            assertTrue("$error", context.getString(res).isNotBlank())
        }
    }

    @Test
    fun blockedHasKickSpecificCopy() {
        assertNotEquals(LurkError.Unexpected.messageRes(), LurkError.Blocked.messageRes())
    }
}
