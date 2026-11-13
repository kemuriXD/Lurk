package io.github.kemurixd.lurk.login

import android.app.Application
import android.view.ViewGroup
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LoginWebViewTest {
    @Test
    fun webViewFillsItsSlot() {
        val view = loginWebView(RuntimeEnvironment.getApplication(), onPageFinished = {})

        // Wrap-content leaves the page's html element 0 tall, so 100%-height pages paint nothing.
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, view.layoutParams?.width)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, view.layoutParams?.height)
    }
}
