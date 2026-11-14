package io.github.kemurixd.lurk.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.designsystem.ErrorAction
import io.github.kemurixd.lurk.designsystem.R
import io.github.kemurixd.lurk.designsystem.action
import io.github.kemurixd.lurk.designsystem.forPlatform
import io.github.kemurixd.lurk.designsystem.labelRes
import io.github.kemurixd.lurk.designsystem.messageRes
import kotlinx.coroutines.delay
import kotlin.math.ceil

private const val SECOND_MILLIS = 1_000L

/**
 * How long a retry runs before its button shows a spinner. A retry that answers sooner (a request that fails at once
 * while offline) shows no progress at all, which keeps the screen still instead of flashing a spinner.
 */
internal const val RETRY_PROGRESS_DELAY_MILLIS = 300L

/** The share of the banner's text width the action may take and still sit beside the text. */
private const val ACTION_BESIDE_SHARE = 0.4f

private val StatePadding = PaddingValues(horizontal = 24.dp, vertical = 32.dp)
private val BannerPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 8.dp)

/**
 * Full-screen failure, for a first load with nothing else to show: a tonal circle with the error's icon, [title]
 * ("Couldn't load categories"), the reason, and one filled button for [action]. [platform] is the one Sign in opens.
 * While [retrying], the Retry button stays, disabled, and shows a small spinner once the retry has run a moment.
 */
@Composable
fun ErrorState(
    title: String,
    error: LurkError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    platform: Platform? = null,
    action: ErrorAction? = error.action(firstPage = true),
    retrying: Boolean = false,
) {
    val tone = error.tone()
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .testTag(ErrorTags.STATE)
                // One announcement: the title and the reason together. The button stays its own node.
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
                .padding(StatePadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(80.dp).background(tone.container(), CircleShape), contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(error.icon()),
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = tone.onContainer(),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(error.messageRes()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        // The full screen's title already says what failed, and its Retry may ask several platforms, so the button
        // keeps its plain label.
        action.forPlatform(platform)?.let {
            ErrorActionButton(it, platform, onRetry, filled = true, namesPlatform = false, retrying = retrying)
        }
    }
}

/**
 * Inline failure at the top of a list that still shows something (Figma Banner): a full-width rounded card with the
 * error's icon, [title], one line of reason and a text action at the right. [reason] replaces the error's own copy
 * where the screen knows better. While [retrying], the action stays, disabled, and shows a small spinner once the retry
 * has run a moment.
 */
@Composable
fun ErrorBanner(
    title: String,
    error: LurkError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    platform: Platform? = null,
    action: ErrorAction? = error.action(firstPage = true),
    reason: String? = null,
    retrying: Boolean = false,
) {
    BannerCard(
        title = title,
        reason = reason ?: stringResource(error.messageRes()),
        error = error,
        action = action,
        platform = platform,
        onRetry = onRetry,
        modifier = modifier.testTag(ErrorTags.BANNER),
        retrying = retrying,
    )
}

/**
 * End-of-list failure: the banner, saying "Couldn't load more from [platform]". It offers Retry only when asking again
 * can help, since what loaded stays on screen either way.
 */
@Composable
fun ErrorFooter(
    platform: Platform,
    error: LurkError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    action: ErrorAction? = error.action(),
    retrying: Boolean = false,
) = ErrorFooter(listOf(platform), error, onRetry, modifier, action, retrying)

@Composable
internal fun BannerCard(
    title: String,
    reason: String,
    error: LurkError,
    action: ErrorAction?,
    platform: Platform?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    retrying: Boolean = false,
) {
    val tone = error.tone()
    val onContainer = tone.onContainer()
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .background(tone.container(), MaterialTheme.shapes.medium)
                // One announcement: the title and the reason together. The button stays its own node.
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
                .padding(BannerPadding),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(error.icon()),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = onContainer,
        )
        TextBesideOrAboveAction(
            text = {
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text(text = title, style = MaterialTheme.typography.titleSmall, color = onContainer)
                    Text(text = reason, style = MaterialTheme.typography.bodyMedium, color = onContainer)
                }
            },
            action =
                action.forPlatform(platform)?.let {
                    { ErrorActionButton(it, platform, onRetry, filled = false, namesPlatform = true, retrying = retrying) }
                },
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * The text with the action at its right, as in Figma. When the action would leave the text too little room (a long
 * label, large fonts, a narrow screen), the action drops under the text at the right edge instead.
 */
@Composable
private fun TextBesideOrAboveAction(
    text: @Composable () -> Unit,
    action: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Layout(contents = listOf(text, action ?: {}), modifier = modifier) { (textMeasurables, actionMeasurables), limits ->
        val width = limits.maxWidth
        val gap = 8.dp.roundToPx()
        val actionPlaceable = actionMeasurables.firstOrNull()?.measure(Constraints(maxWidth = width))
        val beside = actionPlaceable == null || actionPlaceable.width <= width * ACTION_BESIDE_SHARE
        val textWidth = if (actionPlaceable != null && beside) width - actionPlaceable.width - gap else width
        val textPlaceable = textMeasurables.first().measure(Constraints(maxWidth = textWidth.coerceAtLeast(0)))
        val actionHeight = actionPlaceable?.height ?: 0
        val height = if (beside) maxOf(textPlaceable.height, actionHeight) else textPlaceable.height + actionHeight
        layout(width, height) {
            if (beside) {
                textPlaceable.place(0, (height - textPlaceable.height) / 2)
                actionPlaceable?.place(width - actionPlaceable.width, (height - actionHeight) / 2)
            } else {
                textPlaceable.place(0, 0)
                actionPlaceable?.place(width - actionPlaceable.width, textPlaceable.height)
            }
        }
    }
}

/**
 * The action's button. Retry with a known wait counts the seconds down and stays off until they run out, and counts
 * down again after each try, since an equal failure would not change the action. With [namesPlatform], Retry names the
 * platform for screen readers ("Retry loading Kick"). While [retrying] a Retry stays on screen but is off, and a small
 * spinner leads its label once the retry has run [RETRY_PROGRESS_DELAY_MILLIS]: a retry that answers sooner shows no
 * progress at all, so a quick failure while offline never flashes.
 */
@Composable
private fun ErrorActionButton(
    action: ErrorAction,
    platform: Platform?,
    onRetry: () -> Unit,
    filled: Boolean,
    namesPlatform: Boolean,
    retrying: Boolean = false,
) {
    val handlers = LocalErrorHandlers.current
    val busy = retrying && action is ErrorAction.Retry
    val showProgress = rememberDelayed(busy, RETRY_PROGRESS_DELAY_MILLIS)
    val wait = (action as? ErrorAction.Retry)?.after
    var tries by remember(action) { mutableIntStateOf(0) }
    var secondsLeft by remember(action, tries) {
        mutableLongStateOf(wait?.let { ceil(it.inWholeMilliseconds / SECOND_MILLIS.toDouble()).toLong() } ?: 0L)
    }
    LaunchedEffect(action, tries) {
        while (secondsLeft > 0) {
            delay(SECOND_MILLIS)
            secondsLeft--
        }
    }
    val waiting = secondsLeft > 0
    val label =
        if (waiting) {
            stringResource(R.string.error_action_retry_in, secondsLeft)
        } else {
            stringResource(action.labelRes())
        }
    val description =
        if (namesPlatform && platform != null && action.isReadyRetry(waiting)) {
            stringResource(R.string.error_retry_cd, stringResource(platform.labelRes))
        } else {
            null
        }
    val loading = stringResource(R.string.loading)
    val buttonModifier =
        Modifier.heightIn(min = 48.dp).then(
            if (description != null) Modifier.semantics { contentDescription = description } else Modifier,
        ).then(if (busy) Modifier.semantics { stateDescription = loading } else Modifier)
    val onClick = {
        handlers.perform(action, platform, onRetry)
        if (wait != null) tries++
    }
    LogoActionButton(
        label = label,
        // Sign in is for one platform, so its logo leads the label.
        logo = platform.takeIf { action is ErrorAction.SignIn },
        filled = filled,
        enabled = !waiting && !busy,
        onClick = onClick,
        modifier = buttonModifier,
        progress = showProgress,
    )
}

/** True once [active] has held for [delayMillis]; false the moment it ends, and never at all for a shorter hold. */
@Composable
private fun rememberDelayed(
    active: Boolean,
    delayMillis: Long,
): Boolean {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(active) {
        if (active) {
            delay(delayMillis)
            shown = true
        } else {
            shown = false
        }
    }
    return shown && active
}

private fun ErrorAction.isReadyRetry(waiting: Boolean): Boolean = this is ErrorAction.Retry && !waiting

/** Figma Banner tones: Info for being offline or signed out, Warning for a platform change, Error for the rest. */
private enum class Tone { INFO, WARNING, ERROR }

private fun LurkError.tone(): Tone =
    when (this) {
        LurkError.Offline, LurkError.AuthExpired, LurkError.NotSignedIn -> Tone.INFO
        LurkError.SchemaChanged, LurkError.IntegrityRequired -> Tone.WARNING
        else -> Tone.ERROR
    }

@Composable
private fun Tone.container(): Color =
    when (this) {
        Tone.INFO -> MaterialTheme.colorScheme.secondaryContainer
        Tone.WARNING -> MaterialTheme.colorScheme.tertiaryContainer
        Tone.ERROR -> MaterialTheme.colorScheme.errorContainer
    }

@Composable
private fun Tone.onContainer(): Color =
    when (this) {
        Tone.INFO -> MaterialTheme.colorScheme.onSecondaryContainer
        Tone.WARNING -> MaterialTheme.colorScheme.onTertiaryContainer
        Tone.ERROR -> MaterialTheme.colorScheme.onErrorContainer
    }

@DrawableRes
private fun LurkError.icon(): Int =
    when (tone()) {
        Tone.INFO -> if (this == LurkError.Offline) R.drawable.ic_cloud_off else R.drawable.ic_info
        Tone.WARNING, Tone.ERROR -> R.drawable.ic_error
    }
