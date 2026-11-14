package io.github.kemurixd.lurk.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.designsystem.ErrorAction
import io.github.kemurixd.lurk.designsystem.R
import io.github.kemurixd.lurk.designsystem.action
import io.github.kemurixd.lurk.designsystem.labelRes
import io.github.kemurixd.lurk.designsystem.messageRes

/**
 * The footer for several platforms whose next page failed the same way: one banner naming them all, whose [onRetry]
 * asks each again. A sign-in action is per platform, so it never shows here. [retrying] is as for [ErrorBanner].
 */
@Composable
fun ErrorFooter(
    platforms: List<Platform>,
    error: LurkError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    action: ErrorAction? = error.action(),
    retrying: Boolean = false,
) {
    BannerCard(
        title = stringResource(R.string.error_load_more, platformNames(platforms)),
        reason = stringResource(error.messageRes()),
        error = error,
        action = action,
        platform = platforms.singleOrNull(),
        onRetry = onRetry,
        modifier = modifier.testTag(ErrorTags.FOOTER),
        retrying = retrying,
    )
}

/** The platforms' names as one phrase: "Twitch", "Twitch or Kick", "Twitch, Kick or X". */
@Composable
internal fun platformNames(platforms: List<Platform>): String =
    platformList(platforms.map { stringResource(it.labelRes) })

@Composable
internal fun platformList(names: List<String>): String {
    if (names.size < 2) return names.firstOrNull().orEmpty()
    val head = names.dropLast(1).reduce { all, name -> stringResource(R.string.error_list_join, all, name) }
    return stringResource(R.string.error_list_last, head, names.last())
}
