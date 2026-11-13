package io.github.kemurixd.lurk.feature.channel

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.model.Channel
import io.github.kemurixd.lurk.core.model.Stream
import io.github.kemurixd.lurk.feature.common.PlatformSlice

/** The Follow button. It exists only while the user is signed in to the channel's platform. */
sealed interface FollowState {
    /** Signed out of this platform: no Follow button. */
    data object Hidden : FollowState

    /** Signed in, and the follow list is still loading. Shown disabled; a tap does nothing. */
    data object Checking : FollowState

    /** Signed in, but the follow list couldn't be read. The button works as "Follow". */
    data object Unknown : FollowState

    data object Following : FollowState

    data object NotFollowing : FollowState

    /** A follow change to [target] is in flight. Shown optimistically; a failure puts the old state back. */
    data class Busy(
        val target: Boolean,
    ) : FollowState
}

/** The Channel page: the channel's profile and the Follow button. */
data class ChannelUiState(
    val load: PlatformSlice<Channel> = PlatformSlice.Loading,
    val follow: FollowState = FollowState.Hidden,
)

/** What the Channel page can ask of [ChannelViewModel]. */
sealed interface ChannelIntent {
    /** Follow or unfollow. Kick sends the change; Twitch opens the channel's page on twitch.tv. */
    data object ToggleFollow : ChannelIntent

    data object Share : ChannelIntent

    /** Loads the channel again, from the error state. */
    data object Retry : ChannelIntent

    /** Opens the live stream. Does nothing while the channel is offline. */
    data object WatchLive : ChannelIntent
}

/** One-shot results for the screen to act on. Each is delivered to one collector, once. */
sealed interface ChannelEffect {
    data class OpenExternal(
        val url: String,
    ) : ChannelEffect

    data class ShareText(
        val text: String,
    ) : ChannelEffect

    data class OpenPlayer(
        val stream: Stream,
    ) : ChannelEffect

    data class ShowError(
        val error: LurkError,
    ) : ChannelEffect
}
