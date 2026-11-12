package io.github.kemurixd.lurk.platform.kick.realtime

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.config.KickConfig
import io.github.kemurixd.lurk.core.config.RemoteConfig
import io.github.kemurixd.lurk.platform.kick.api.KickApi
import io.github.kemurixd.lurk.platform.kick.api.KickBase
import io.github.kemurixd.lurk.platform.kick.api.asPathSegment
import io.ktor.http.HttpMethod
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val CENTRIFUGO = "centrifugo"
private const val PUSHER = "pusher"
private const val WEBSOCKET_MODE = "websocket"

/** Where and how to open one Kick realtime socket. The connection token is short-lived and never printed. */
data class RealtimeTicket(
    val socketUrl: String,
    val connectToken: String,
    val channels: List<String>,
) {
    override fun toString(): String =
        "RealtimeTicket(socketUrl=$socketUrl, connectToken=<redacted>, channels=$channels)"
}

/**
 * Asks Kick's web API for a realtime socket the way kick.com does: the global connection descriptor, the chat
 * connection descriptor for the channel, then a connection token for the client id. Every call to [open] runs all
 * three, so a reconnect always gets a fresh token. Only a Centrifugo socket can be opened: when the server picks
 * Pusher, or a reply is not the expected shape, the result is `SchemaChanged`.
 */
class RealtimeBootstrap(
    private val api: KickApi,
    private val config: StateFlow<RemoteConfig>,
    private val newClientId: () -> String = ::randomClientId,
) {
    /** [userId] is the signed-in user's id when known; without it every call uses a fresh random client id. */
    suspend fun open(
        channelId: String,
        chatroomId: String,
        token: String?,
        userId: String? = null,
    ): Outcome<RealtimeTicket> {
        val kick = config.value.kick
        val clientId = userId ?: newClientId()
        val descriptorBody = connectionBody(clientId, kick.realtimeAcceptedProviders)
        val chatPath = kick.realtimeChatConnectionPath.replace("{channelId}", channelId.asPathSegment())
        return post(kick.realtimeConnectionPath, token, descriptorBody)
            .then { post(chatPath, token, descriptorBody) }
            .then { reply -> socketUrlOf(reply).orSchemaChanged() }
            .then { socketUrl ->
                post(kick.realtimeAuthPath, token, buildJsonObject { put("client_id", clientId) })
                    .then { reply ->
                        reply
                            .obj("data")
                            ?.text("token")
                            ?.takeIf { it.isNotBlank() }
                            .orSchemaChanged()
                    }.then { connectToken ->
                        Outcome.Success(
                            RealtimeTicket(socketUrl, connectToken, channelsOf(kick, channelId, chatroomId)),
                        )
                    }
            }
    }

    override fun toString(): String = "RealtimeBootstrap"

    private suspend fun post(
        path: String,
        token: String?,
        body: JsonElement,
    ): Outcome<JsonElement?> = api.send(HttpMethod.Post, KickBase.WEB, path, token, body)
}

/** The body kick.com sends to both connection routes. */
private fun connectionBody(
    clientId: String,
    providers: List<String>,
): JsonObject =
    buildJsonObject {
        putJsonObject("client") {
            put("id", clientId)
            put("type", "web")
        }
        putJsonObject("capabilities") {
            putJsonArray("accepted_providers") { providers.forEach { addJsonObject { put("provider", it) } } }
        }
    }

/**
 * The socket URL from a connection descriptor, chosen like kick.com does: the first connection with usable
 * credentials wins. Null when that one is Pusher (Lurk has no Pusher transport), when none is usable, or when the
 * mode is not a websocket.
 */
private fun socketUrlOf(reply: JsonElement?): String? {
    val data = reply.obj("data")
    val chosen =
        if (data?.text("mode") == WEBSOCKET_MODE) data["connections"].objects().firstOrNull(::isUsable) else null
    return if (chosen?.text("provider") == CENTRIFUGO) chosen.obj("credentials")?.text("url") else null
}

private fun isUsable(connection: JsonObject): Boolean {
    val credentials = connection.obj("credentials") ?: return false
    return when (connection.text("provider")) {
        PUSHER -> !credentials.text("app_key").isNullOrEmpty() && !credentials.text("cluster").isNullOrEmpty()
        CENTRIFUGO -> !credentials.text("url").isNullOrEmpty()
        else -> false
    }
}

private fun channelsOf(
    kick: KickConfig,
    channelId: String,
    chatroomId: String,
): List<String> =
    kick.publicChannelTemplates.map { it.replace("{chatroomId}", chatroomId).replace("{channelId}", channelId) }

private inline fun <T, R> Outcome<T>.then(next: (T) -> Outcome<R>): Outcome<R> =
    when (this) {
        is Outcome.Success -> next(value)
        is Outcome.Failure -> this
    }

private fun <T : Any> T?.orSchemaChanged(): Outcome<T> =
    if (this == null) Outcome.Failure(LurkError.SchemaChanged) else Outcome.Success(this)

private fun JsonElement?.objects(): List<JsonObject> = (this as? JsonArray).orEmpty().filterIsInstance<JsonObject>()

private fun JsonElement?.obj(key: String): JsonObject? = (this as? JsonObject)?.get(key) as? JsonObject

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

@OptIn(ExperimentalUuidApi::class)
private fun randomClientId(): String = Uuid.random().toString()
