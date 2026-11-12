package io.github.kemurixd.lurk.emotes.seventv

import co.touchlab.kermit.Logger
import io.github.kemurixd.lurk.core.model.Badge
import io.github.kemurixd.lurk.core.model.Emote
import io.github.kemurixd.lurk.core.model.EmoteScope
import io.github.kemurixd.lurk.core.model.Paint
import io.github.kemurixd.lurk.core.model.PaintFunction
import io.github.kemurixd.lurk.core.model.PaintShadow
import io.github.kemurixd.lurk.core.model.PaintStop
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.emotes.provider.SevenTvParser
import io.github.kemurixd.lurk.emotes.provider.providerJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

/** One effect of a dispatch. Kinds are 7TV's names: `PAINT`, `BADGE` or `EMOTE_SET`. */
sealed interface SevenTvChange {
    /**
     * The emote comes with [Emote.scope] CHANNEL; whoever applies it knows whether the set is the channel's or a
     * personal one.
     */
    data class EmoteAdded(
        val setId: String,
        val emote: Emote,
    ) : SevenTvChange

    data class EmoteRemoved(
        val setId: String,
        val emoteId: String,
    ) : SevenTvChange

    data class PaintDefined(
        val paint: Paint,
    ) : SevenTvChange

    data class BadgeDefined(
        val id: String,
        val badge: Badge,
    ) : SevenTvChange

    /** [userId] is the user's id on the platform being watched, the id chat messages carry. */
    data class Entitled(
        val kind: String,
        val refId: String,
        val userId: String,
    ) : SevenTvChange

    data class Unentitled(
        val kind: String,
        val refId: String,
        val userId: String,
    ) : SevenTvChange
}

internal const val KIND_PAINT = "PAINT"
internal const val KIND_BADGE = "BADGE"
internal const val KIND_EMOTE_SET = "EMOTE_SET"

/** Maps dispatch bodies to [SevenTvChange]s. Never throws: a body it cannot read gives no change and is logged. */
object SevenTvDispatch {
    private val log = Logger.withTag("SevenTvDispatch")
    private const val EMOTES_KEY = "emotes"

    fun changes(
        d: EventApiMessage.Dispatch,
        platform: Platform,
    ): List<SevenTvChange> =
        try {
            when (d.type) {
                "emote_set.update" -> emoteSetUpdate(d.body)
                "cosmetic.create" -> listOfNotNull(cosmetic(d.body))
                "entitlement.create" -> listOfNotNull(entitlement(d.body, platform, created = true))
                "entitlement.delete" -> listOfNotNull(entitlement(d.body, platform, created = false))
                else -> emptyList()
            }
        } catch (e: SerializationException) {
            // The exception message quotes the body, which can hold other users' 7TV names.
            log.w { "Skipping 7TV ${d.type} dispatch that failed to decode (${e::class.simpleName})" }
            emptyList()
        } catch (e: IllegalArgumentException) {
            log.w { "Skipping 7TV ${d.type} dispatch that failed to decode (${e::class.simpleName})" }
            emptyList()
        }

    /** `pushed` values are added, `pulled` old values removed, and an `updated` entry (a rename) is both. */
    private fun emoteSetUpdate(body: JsonObject): List<SevenTvChange> {
        val setId = body.string("id") ?: return emptyList()

        fun entries(key: String) =
            (body[key] as? JsonArray).orEmpty().filterIsInstance<JsonObject>().filter {
                it.string("key") == EMOTES_KEY
            }

        fun added(entry: JsonObject) =
            entry["value"]?.let { SevenTvParser.activeEmote(it, EmoteScope.CHANNEL) }?.let {
                SevenTvChange.EmoteAdded(setId, it)
            }

        fun removed(entry: JsonObject) =
            entry.obj("old_value")?.string("id")?.let { SevenTvChange.EmoteRemoved(setId, it) }

        return entries("pushed").mapNotNull(::added) +
            entries("pulled").mapNotNull(::removed) +
            entries("updated").flatMap { listOfNotNull(removed(it), added(it)) }
    }

    private fun cosmetic(body: JsonObject): SevenTvChange? {
        val obj = body.obj("object")
        val data = obj?.obj("data")
        val id = data?.string("id") ?: obj?.string("id")
        if (data == null || id == null) return null
        return when (obj?.string("kind")) {
            KIND_PAINT -> {
                paint(id, providerJson.decodeFromJsonElement(data))?.let(SevenTvChange::PaintDefined)
            }

            KIND_BADGE -> {
                badge(
                    id,
                    providerJson.decodeFromJsonElement(data),
                )?.let { SevenTvChange.BadgeDefined(id, it) }
            }

            else -> {
                null
            }
        }
    }

    /** The user is identified by their connection for [platform]; a user with none there gives no change. */
    private fun entitlement(
        body: JsonObject,
        platform: Platform,
        created: Boolean,
    ): SevenTvChange? {
        val obj = body.obj("object")
        val kind = obj?.string("kind")
        val refId = obj?.string("ref_id")
        val userId =
            (obj?.obj("user")?.get("connections") as? JsonArray)
                .orEmpty()
                .filterIsInstance<JsonObject>()
                .firstOrNull { it.string("platform") == platform.name }
                ?.string("id")
        return when {
            kind == null || refId == null || userId == null -> null
            created -> SevenTvChange.Entitled(kind, refId, userId)
            else -> SevenTvChange.Unentitled(kind, refId, userId)
        }
    }

    private fun paint(
        id: String,
        data: PaintData,
    ): Paint? {
        val function = PaintFunction.entries.firstOrNull { it.name == data.function }
        if (function == null) {
            log.w { "Skipping 7TV paint with unknown function ${data.function}" }
            return null
        }
        return Paint(
            id = id,
            name = data.name,
            function = function,
            angle = data.angle.toInt(),
            repeat = data.repeat,
            stops = data.stops.map { PaintStop(it.at, argb(it.color)) },
            imageUrl = data.imageUrl?.takeIf { it.isNotBlank() },
            shadows = data.shadows.map { PaintShadow(it.xOffset, it.yOffset, it.radius, argb(it.color)) },
        )
    }

    /** The largest WebP rendition, by width. */
    private fun badge(
        id: String,
        data: BadgeData,
    ): Badge? {
        val file =
            data.host.files
                .filter { it.format.equals("WEBP", ignoreCase = true) }
                .maxByOrNull { it.width } ?: return null
        val base = if (data.host.url.startsWith("//")) "https:${data.host.url}" else data.host.url
        return Badge(
            setId = id,
            version = data.tag ?: id,
            title = data.tooltip ?: data.name,
            imageUrl = "$base/${file.name}",
        )
    }

    /** 7TV colours are signed 32-bit RGBA; Lurk's model is ARGB. */
    private fun argb(rgba: Long): Int {
        val value = rgba.toInt()
        return (value ushr BYTE) or ((value and LOW_BYTE) shl ALPHA_SHIFT)
    }

    private const val BYTE = 8
    private const val LOW_BYTE = 0xFF
    private const val ALPHA_SHIFT = 24

    @Serializable
    private class PaintData(
        val name: String = "",
        val function: String = "",
        val repeat: Boolean = false,
        val angle: Double = 0.0,
        @SerialName("image_url") val imageUrl: String? = null,
        val stops: List<StopData> = emptyList(),
        val shadows: List<ShadowData> = emptyList(),
    )

    @Serializable
    private class StopData(
        val at: Float,
        val color: Long,
    )

    @Serializable
    private class ShadowData(
        @SerialName("x_offset") val xOffset: Float = 0f,
        @SerialName("y_offset") val yOffset: Float = 0f,
        val radius: Float = 0f,
        val color: Long,
    )

    @Serializable
    private class BadgeData(
        val name: String? = null,
        val tag: String? = null,
        val tooltip: String? = null,
        val host: Host,
    )

    @Serializable
    private class Host(
        val url: String,
        val files: List<HostFile> = emptyList(),
    )

    @Serializable
    private class HostFile(
        val name: String,
        val width: Int = 0,
        val format: String = "",
    )
}
