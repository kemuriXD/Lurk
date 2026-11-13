package io.github.kemurixd.lurk

import android.app.Application
import android.content.Context
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser
import androidx.core.splashscreen.R as SplashR

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SplashThemeTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun startingTheme(): Resources.Theme =
        context.resources.newTheme().apply { applyStyle(R.style.Theme_Lurk_Starting, true) }

    private fun Resources.Theme.resourceId(attr: Int): Int {
        val a = obtainStyledAttributes(intArrayOf(attr))
        try {
            return a.getResourceId(0, 0)
        } finally {
            a.recycle()
        }
    }

    @Test
    @Config(sdk = [31])
    fun startingThemeHasPeekIcon() {
        assertEquals(
            R.drawable.splash_peek,
            startingTheme().resourceId(SplashR.attr.windowSplashScreenAnimatedIcon),
        )
    }

    @Test
    fun animationDurationAtMost1000() {
        val a = startingTheme().obtainStyledAttributes(intArrayOf(SplashR.attr.windowSplashScreenAnimationDuration))
        val duration = a.getInt(0, -1)
        a.recycle()
        assertTrue("duration was $duration", duration in 1..1000)
    }

    @Test
    fun postSplashThemeIsApp() {
        assertEquals(
            R.style.Theme_Lurk,
            startingTheme().resourceId(SplashR.attr.postSplashScreenTheme),
        )
    }

    @Test
    fun launcherIconIsAdaptiveWithMonochrome() {
        val parser = context.resources.getXml(R.mipmap.ic_launcher)
        val layers = mutableMapOf<String, Int>()
        var root: String? = null
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            if (root == null) root = parser.name
            layers[parser.name] = parser.getAttributeResourceValue(ANDROID_NS, "drawable", 0)
        }
        assertEquals("adaptive-icon", root)
        assertEquals(R.drawable.ic_launcher_foreground, layers["foreground"])
        assertEquals(R.drawable.ic_launcher_background, layers["background"])
        assertEquals(R.drawable.ic_launcher_monochrome, layers["monochrome"])
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
