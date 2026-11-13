package io.github.kemurixd.lurk.login

import android.webkit.CookieManager
import android.webkit.WebStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** The login WebView's cookie store. A seam so the login screen can be tested without a real `CookieManager`. */
interface CookieJar {
    /** The `Cookie` header the WebView would send to [url] (`a=1; b=2`), or null when there is none. */
    fun read(url: String): String?

    /** Removes every cookie and all web storage, and returns once they are gone from disk too. */
    suspend fun clearAll()
}

/** [CookieJar] over the app-wide WebView [CookieManager]. */
class AndroidCookieJar : CookieJar {
    override fun read(url: String): String? = CookieManager.getInstance().getCookie(url)

    // CookieManager wants a thread with a Looper and calls back on it, so this runs on the main thread.
    override suspend fun clearAll() =
        withContext(Dispatchers.Main) {
            val cookies = CookieManager.getInstance()
            suspendCancellableCoroutine { done -> cookies.removeAllCookies { done.resume(Unit) } }
            cookies.flush()
            WebStorage.getInstance().deleteAllData()
        }
}
