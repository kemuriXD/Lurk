package io.github.kemurixd.lurk.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class EmoteProvider {
    @SerialName("twitch")
    TWITCH,

    @SerialName("kick")
    KICK,

    @SerialName("7tv")
    SEVENTV,

    @SerialName("bttv")
    BTTV,

    @SerialName("ffz")
    FFZ,
}

@Serializable
enum class EmoteScope {
    @SerialName("global")
    GLOBAL,

    @SerialName("channel")
    CHANNEL,

    @SerialName("personal")
    PERSONAL,
}

/** One rendition of an emote; [scale] is 1, 2, 3 or 4. */
@Serializable
data class EmoteImage(
    val scale: Int,
    val url: String,
)

/** FrankerFaceZ modifier effects, one bit each in the order FrankerFaceZ declares them (`MODIFIER_FLAGS`). */
@Suppress("MagicNumber")
@Serializable
enum class FfzEffect(
    val bit: Int,
) {
    @SerialName("hidden")
    HIDDEN(1 shl 0),

    @SerialName("flip_x")
    FLIP_X(1 shl 1),

    @SerialName("flip_y")
    FLIP_Y(1 shl 2),

    @SerialName("grow_x")
    GROW_X(1 shl 3),

    @SerialName("slide")
    SLIDE(1 shl 4),

    @SerialName("appear")
    APPEAR(1 shl 5),

    @SerialName("leave")
    LEAVE(1 shl 6),

    @SerialName("rotate")
    ROTATE(1 shl 7),

    @SerialName("rotate_90")
    ROTATE_90(1 shl 8),

    @SerialName("greyscale")
    GREYSCALE(1 shl 9),

    @SerialName("sepia")
    SEPIA(1 shl 10),

    @SerialName("rainbow")
    RAINBOW(1 shl 11),

    @SerialName("hyper_red")
    HYPER_RED(1 shl 12),

    @SerialName("shake")
    SHAKE(1 shl 13),

    @SerialName("cursed")
    CURSED(1 shl 14),

    @SerialName("jam")
    JAM(1 shl 15),

    @SerialName("bounce")
    BOUNCE(1 shl 16),

    @SerialName("no_space")
    NO_SPACE(1 shl 17),
    ;

    companion object {
        fun fromFlags(flags: Int): Set<FfzEffect> = entries.filterTo(mutableSetOf()) { flags and it.bit != 0 }
    }
}

private const val SEXUAL_BIT = 1 shl 16
private const val EPILEPSY_BIT = 1 shl 17
private const val EDGY_BIT = 1 shl 18

/**
 * An emote from any provider. Visibility flags are carried, never used to drop the emote; [contentFlags] keeps
 * 7TV's raw bits so unknown ones (such as TwitchDisallowed, bit 24) survive.
 */
@Serializable
data class Emote(
    val id: String,
    val name: String,
    val provider: EmoteProvider,
    val scope: EmoteScope,
    val images: List<EmoteImage>,
    val animated: Boolean = false,
    val zeroWidth: Boolean = false,
    val listed: Boolean = true,
    val contentFlags: Int = 0,
    val modifierEffects: Set<FfzEffect> = emptySet(),
    val hiddenInPicker: Boolean = false,
) {
    val isSexual: Boolean get() = contentFlags and SEXUAL_BIT != 0
    val isEpilepsy: Boolean get() = contentFlags and EPILEPSY_BIT != 0
    val isEdgy: Boolean get() = contentFlags and EDGY_BIT != 0
    val isModifier: Boolean get() = modifierEffects.isNotEmpty()
}
