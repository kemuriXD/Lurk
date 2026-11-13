package io.github.kemurixd.lurk.designsystem.time

import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import io.github.kemurixd.lurk.designsystem.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SpokenUptimeTest {
    private val base: Resources = ApplicationProvider.getApplicationContext<android.content.Context>().resources

    /** A language that joins the two the other way round, with its own word: only the resource says how. */
    @Suppress("DEPRECATION")
    private val other =
        object : Resources(base.assets, base.displayMetrics, base.configuration) {
            override fun getString(
                id: Int,
                vararg formatArgs: Any?,
            ): String =
                if (id == R.string.cd_uptime_hours_minutes) {
                    "${formatArgs[1]} et ${formatArgs[0]}"
                } else {
                    base.getString(id, *formatArgs)
                }

            override fun getQuantityString(
                id: Int,
                quantity: Int,
                vararg formatArgs: Any?,
            ): String = base.getQuantityString(id, quantity, *formatArgs)
        }

    @Test
    fun hoursAndMinutesAreJoinedByAResource() {
        assertEquals("live for 49 minutes et 1 hour", spokenUptime(1.hours + 49.minutes, other))
    }

    @Test
    fun theDefaultJoinerIsASpace() {
        assertEquals("live for 1 hour 49 minutes", spokenUptime(1.hours + 49.minutes, base))
    }
}
