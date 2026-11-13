package io.github.kemurixd.lurk.image

import android.content.Context
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

private const val MISSING_PREVIEW_PATH = "/404_preview"
private const val NOT_FOUND = 404

/**
 * Turns a redirect to Twitch's grey "no preview" image into a 404. The image then fails, so the card shows its own
 * placeholder, and Coil caches nothing, because it stores only successful replies. Twitch redirects there when a
 * channel is offline or a new broadcast has no frame yet.
 */
internal class MissingPreviewInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (MISSING_PREVIEW_PATH !in response.request.url.encodedPath) return response
        response.close()
        return response
            .newBuilder()
            .code(NOT_FOUND)
            .message("No preview yet")
            .body("".toResponseBody())
            .build()
    }
}

/** The app's image loader: Coil's defaults, over an OkHttp client that treats a missing preview as missing. */
internal fun lurkImageLoader(context: Context): ImageLoader =
    ImageLoader
        .Builder(context)
        .components {
            add(
                OkHttpNetworkFetcherFactory(
                    callFactory = { OkHttpClient.Builder().addInterceptor(MissingPreviewInterceptor()).build() },
                ),
            )
        }.build()
