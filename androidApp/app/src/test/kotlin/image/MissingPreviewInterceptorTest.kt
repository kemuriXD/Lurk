package io.github.kemurixd.lurk.image

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MissingPreviewInterceptorTest {
    private val server = MockWebServer()
    private val client = OkHttpClient.Builder().addInterceptor(MissingPreviewInterceptor()).build()

    @After
    fun tearDown() {
        server.close()
    }

    private fun get(path: String) = client.newCall(Request.Builder().url(server.url(path)).build()).execute()

    @Test
    fun aRedirectToTheGreyPlaceholderIsAMissingImage() {
        server.start()
        server.enqueue(
            MockResponse
                .Builder()
                .code(302)
                .addHeader("Location", "/ttv-static/404_preview-440x248.jpg")
                .build(),
        )
        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .body("grey")
                .build(),
        )

        get("/previews-ttv/live_user_streamer1-440x248.jpg?t=1-2").use { response ->
            assertEquals(404, response.code)
            assertFalse(response.isSuccessful)
            assertEquals("", response.body.string())
        }
    }

    @Test
    fun aRealPreviewPassesThrough() {
        server.start()
        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .body("frame")
                .build(),
        )

        get("/previews-ttv/live_user_streamer1-440x248.jpg?t=1-2").use { response ->
            assertTrue(response.isSuccessful)
            assertEquals("frame", response.body.string())
        }
    }
}
