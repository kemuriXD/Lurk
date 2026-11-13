package io.github.kemurixd.lurk.designsystem.component

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.designsystem.R

/**
 * The placement rule for first-page failures in a list fed by several platforms. When [nothingElse] is true (every
 * platform failed and nothing shows), one full-width [ErrorState] titled [allFailedTitle], whose Retry asks each failed
 * platform again. Otherwise one [ErrorBanner] per failed platform, at the top of the list. [onRetry] asks one again.
 */
fun LazyGridScope.platformErrors(
    failures: List<Pair<Platform, LurkError>>,
    nothingElse: Boolean,
    @StringRes allFailedTitle: Int,
    onRetry: (Platform) -> Unit,
) {
    if (failures.isEmpty()) return
    if (nothingElse) {
        val (platform, error) = failures.first()
        item(key = "failed", span = { GridItemSpan(maxLineSpan) }, contentType = "failed") {
            ErrorState(
                title = stringResource(allFailedTitle),
                error = error,
                onRetry = { failures.forEach { (failed, _) -> onRetry(failed) } },
                platform = platform,
            )
        }
        return
    }
    failures.forEach { (platform, error) ->
        item(key = "error:${platform.name}", span = { GridItemSpan(maxLineSpan) }, contentType = "error") {
            ErrorBanner(
                title = stringResource(R.string.error_load_platform, stringResource(platform.labelRes)),
                error = error,
                onRetry = { onRetry(platform) },
                platform = platform,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}
