package io.github.kemurixd.lurk.designsystem

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.kemurixd.lurk.core.common.LurkError
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.seconds

/** The one mapping from a failure to what the user can do about it (§6 table, ruling R46). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ErrorActionTest {
    private val retry = ErrorAction.Retry()

    @Test fun offlineRetries() = assertEquals(retry, LurkError.Offline.action())

    @Test fun timeoutRetries() = assertEquals(retry, LurkError.Timeout.action())

    @Test fun rateLimitedWithoutAWaitRetriesAtOnce() = assertEquals(retry, LurkError.RateLimited(null).action())

    @Test
    fun rateLimitedWithAKnownWaitCountsDown() =
        assertEquals(ErrorAction.Retry(after = 30.seconds), LurkError.RateLimited(30.seconds).action())

    @Test fun serverErrorRetries() = assertEquals(retry, LurkError.Http(503).action())

    @Test fun requestTimeoutRetries() = assertEquals(retry, LurkError.Http(408).action())

    @Test fun tooEarlyRetries() = assertEquals(retry, LurkError.Http(425).action())

    @Test fun tooManyRequestsRetries() = assertEquals(retry, LurkError.Http(429).action())

    @Test fun otherClientErrorHasNoAction() = assertEquals(null, LurkError.Http(403).action())

    @Test fun blockedRetries() = assertEquals(retry, LurkError.Blocked.action())

    @Test fun unexpectedRetries() = assertEquals(retry, LurkError.Unexpected.action())

    @Test fun proxyFailedOpensProxySettings() =
        assertEquals(
            ErrorAction.OpenProxySettings,
            LurkError.ProxyFailed.action(),
        )

    @Test fun authExpiredSignsInAgain() = assertEquals(ErrorAction.SignIn(again = true), LurkError.AuthExpired.action())

    @Test fun notSignedInSignsIn() = assertEquals(ErrorAction.SignIn(again = false), LurkError.NotSignedIn.action())

    @Test fun schemaChangedChecksForAnUpdate() =
        assertEquals(
            ErrorAction.CheckForUpdate,
            LurkError.SchemaChanged.action(),
        )

    @Test fun integrityRequiredHasNoAction() = assertEquals(null, LurkError.IntegrityRequired.action())

    @Test fun notFoundGoesBack() = assertEquals(ErrorAction.GoBack, LurkError.NotFound.action())

    @Test fun unsupportedHasNoAction() = assertEquals(null, LurkError.Unsupported.action())

    // A snackbar reports something the user did; leaving the page is never the answer there.
    @Test
    fun snackbarNeverGoesBack() = assertEquals(null, LurkError.NotFound.snackbarAction())

    @Test
    fun snackbarKeepsTheOtherActions() {
        assertEquals(retry, LurkError.Timeout.snackbarAction())
        assertEquals(ErrorAction.SignIn(again = true), LurkError.AuthExpired.snackbarAction())
        assertEquals(null, LurkError.IntegrityRequired.snackbarAction())
    }

    // Ruling R41: a first page always offers Retry where the table offers nothing else.
    @Test
    fun firstPageRetriesWhereTheTableOffersNothing() {
        assertEquals(retry, LurkError.IntegrityRequired.action(firstPage = true))
        assertEquals(retry, LurkError.Unsupported.action(firstPage = true))
        assertEquals(retry, LurkError.Http(403).action(firstPage = true))
    }

    @Test
    fun firstPageKeepsTheTablesOwnAction() {
        assertEquals(ErrorAction.SignIn(again = true), LurkError.AuthExpired.action(firstPage = true))
        assertEquals(ErrorAction.CheckForUpdate, LurkError.SchemaChanged.action(firstPage = true))
        assertEquals(ErrorAction.SignIn(again = false), LurkError.NotSignedIn.action(firstPage = true))
    }

    // No surface shows two actions, so a retryable failure on a first page keeps its Retry.
    @Test
    fun firstPageRetriesAProxyFailureInsteadOfOpeningSettings() =
        assertEquals(retry, LurkError.ProxyFailed.action(firstPage = true))

    @Test
    fun theTablesProxyActionStaysWhereItIsNotAFirstPage() =
        assertEquals(ErrorAction.OpenProxySettings, LurkError.ProxyFailed.action(firstPage = false))

    @Test
    fun aMissingListOnATabRootRetries() = assertEquals(retry, LurkError.NotFound.action(firstPage = true))

    @Test
    fun aMissingDetailPageGoesBack() =
        assertEquals(ErrorAction.GoBack, LurkError.NotFound.action(firstPage = true, canGoBack = true))

    @Test
    fun everyActionHasItsPlainVerbLabel() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val labels =
            listOf(
                ErrorAction.Retry(),
                ErrorAction.SignIn(again = false),
                ErrorAction.SignIn(again = true),
                ErrorAction.OpenProxySettings,
                ErrorAction.CheckForUpdate,
                ErrorAction.GoBack,
            ).map { context.getString(it.labelRes()) }
        assertEquals(
            listOf("Retry", "Sign in", "Sign in again", "Open proxy settings", "Check for update", "Go back"),
            labels,
        )
    }

    @Test
    fun releasesPageIsTheProjectsGitHubReleases() =
        assertEquals("https://github.com/kemuriXD/Lurk/releases", RELEASES_URL)
}
