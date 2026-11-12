package io.github.kemurixd.lurk.emotes.seventv

import io.github.kemurixd.lurk.core.model.Badge
import io.github.kemurixd.lurk.core.model.Paint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * 7TV paints and badges, and which user wears which. Users are keyed by their platform id. Definitions and
 * entitlements arrive independently, so a user entitled to a paint that is not defined yet resolves once it is.
 */
data class Cosmetics(
    val paints: Map<String, Paint> = emptyMap(),
    val badges: Map<String, Badge> = emptyMap(),
    val userPaint: Map<String, String> = emptyMap(),
    val userBadge: Map<String, String> = emptyMap(),
) {
    fun paintFor(userId: String): Paint? = userPaint[userId]?.let { paints[it] }

    fun badgeFor(userId: String): Badge? = userBadge[userId]?.let { badges[it] }
}

/** The cosmetics of one open channel. Updates go through [MutableStateFlow.update], so concurrent callers are safe. */
class CosmeticsRegistry {
    private val mutable = MutableStateFlow(Cosmetics())
    val state: StateFlow<Cosmetics> = mutable

    /** Applies paint and badge changes; emote changes and emote-set entitlements are ignored. */
    fun apply(change: SevenTvChange) =
        mutable.update { current ->
            when (change) {
                is SevenTvChange.PaintDefined -> {
                    current.copy(
                        paints =
                            current.paints + (change.paint.id to change.paint),
                    )
                }

                is SevenTvChange.BadgeDefined -> {
                    current.copy(badges = current.badges + (change.id to change.badge))
                }

                is SevenTvChange.Entitled -> {
                    current.entitle(change.kind, change.refId, change.userId)
                }

                is SevenTvChange.Unentitled -> {
                    current.unentitle(change.kind, change.refId, change.userId)
                }

                is SevenTvChange.EmoteAdded, is SevenTvChange.EmoteRemoved -> {
                    current
                }
            }
        }
}

private fun Cosmetics.entitle(
    kind: String,
    refId: String,
    userId: String,
): Cosmetics =
    when (kind) {
        KIND_PAINT -> copy(userPaint = userPaint + (userId to refId))
        KIND_BADGE -> copy(userBadge = userBadge + (userId to refId))
        else -> this
    }

/** Only clears the user's cosmetic when it is still [refId]: a late delete must not undo a newer entitlement. */
private fun Cosmetics.unentitle(
    kind: String,
    refId: String,
    userId: String,
): Cosmetics =
    when {
        kind == KIND_PAINT && userPaint[userId] == refId -> copy(userPaint = userPaint - userId)
        kind == KIND_BADGE && userBadge[userId] == refId -> copy(userBadge = userBadge - userId)
        else -> this
    }
