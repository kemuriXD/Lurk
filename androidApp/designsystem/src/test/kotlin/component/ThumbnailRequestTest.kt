package io.github.kemurixd.lurk.designsystem.component

import androidx.test.core.app.ApplicationProvider
import coil3.memory.MemoryCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ThumbnailRequestTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun aRefreshedPreviewKeepsTheOneOnScreenWhileItLoads() {
        val request =
            thumbnailRequest(context, "https://cdn.example/t/1.jpg?t=1-2", "https://cdn.example/t/1.jpg?t=1-1")
        assertEquals(MemoryCache.Key("https://cdn.example/t/1.jpg?t=1-1"), request.placeholderMemoryCacheKey)
        assertEquals("https://cdn.example/t/1.jpg?t=1-2", request.data)
    }

    @Test
    fun aFirstLoadHasNoPlaceholderFromTheCache() {
        assertNull(thumbnailRequest(context, "https://cdn.example/t/1.jpg", null).placeholderMemoryCacheKey)
    }

    @Test
    fun theSameUrlDoesNotUseItselfAsAPlaceholder() {
        val url = "https://cdn.example/t/1.jpg"
        assertNull(thumbnailRequest(context, url, url).placeholderMemoryCacheKey)
    }
}
