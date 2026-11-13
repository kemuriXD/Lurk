package io.github.kemurixd.lurk.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene

/** How the app moves between pages. Every page slides sideways; nothing scales or cross-fades. */
internal enum class NavigationKind { PUSH, POP, PREDICTIVE_POP }

/** FORWARD: the new page enters from the right and the old one leaves to the left. BACKWARD: the reverse. */
internal enum class SlideDirection { FORWARD, BACKWARD }

internal class PageTransition(
    val direction: SlideDirection,
    val durationMillis: Int,
    val easing: Easing,
)

private const val SLIDE_MILLIS = 300
private val SlideEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * The slide for moving from the page keyed [from] to the one keyed [to]. A switch between tabs goes the way the tabs
 * lie (a tab further right slides left), whatever the back stack did to get there. Other pushes slide forward and pops
 * slide backward. The back swipe is always the pop slide. Every kind shares one duration and easing, so the whole
 * app moves the same way.
 */
internal fun pageTransition(
    kind: NavigationKind,
    from: Any?,
    to: Any?,
): PageTransition {
    val fromTab = tabOf(from)
    val toTab = tabOf(to)
    val direction =
        when {
            kind != NavigationKind.PREDICTIVE_POP && fromTab != null && toTab != null && fromTab != toTab -> {
                if (toTab.ordinal > fromTab.ordinal) SlideDirection.FORWARD else SlideDirection.BACKWARD
            }

            kind == NavigationKind.PUSH -> {
                SlideDirection.FORWARD
            }

            else -> {
                SlideDirection.BACKWARD
            }
        }
    return PageTransition(direction, SLIDE_MILLIS, SlideEasing)
}

internal fun PageTransition.toContentTransform(): ContentTransform {
    val sign = if (direction == SlideDirection.FORWARD) 1 else -1
    val spec = tween<IntOffset>(durationMillis, easing = easing)
    return slideInHorizontally(spec) { width -> sign * width } togetherWith
        slideOutHorizontally(spec) { width -> -sign * width }
}

private const val TAB_METADATA = "lurk.tab"

/**
 * The metadata every entry for [key] carries: the tab, for a tab's root. A scene hands over only its entries, whose
 * content key is a string and whose key is private, so the tab has to ride along in the metadata.
 */
internal fun routeMetadata(key: NavKey): Map<String, Any> =
    TopLevel.of(key)?.let { mapOf(TAB_METADATA to it) }.orEmpty()

private fun tabOf(page: Any?): TopLevel? = page as? TopLevel ?: TopLevel.of(page as? NavKey)

/** The transition between two scenes, read the way the nav display hands them over. */
internal fun pageTransition(
    kind: NavigationKind,
    from: Scene<NavKey>,
    to: Scene<NavKey>,
): PageTransition =
    pageTransition(
        kind,
        from.entries
            .lastOrNull()
            ?.metadata
            ?.get(TAB_METADATA),
        to.entries
            .lastOrNull()
            ?.metadata
            ?.get(TAB_METADATA),
    )

internal fun AnimatedContentTransitionScope<Scene<NavKey>>.slide(kind: NavigationKind): ContentTransform =
    pageTransition(kind, initialState, targetState).toContentTransform()
