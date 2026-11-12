package io.github.kemurixd.lurk.platform.kick.account

import io.github.kemurixd.lurk.auth.ProfileFetcher
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Account
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.platform.kick.api.KickApi
import io.github.kemurixd.lurk.platform.kick.api.KickBase
import io.github.kemurixd.lurk.platform.kick.discovery.decode
import io.github.kemurixd.lurk.platform.kick.discovery.decodeOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement

/** The account fields of `v1/user`. Nothing else is read, and [toString] shows none of it. */
@Serializable
private data class UserJson(
    val id: Long? = null,
    val username: String? = null,
    @SerialName("profilepic") val profilePic: String? = null,
) {
    override fun toString(): String = "UserJson"
}

/** Looks up the account behind a Kick session token. A token Kick no longer accepts is `AuthExpired`. */
class KickProfileFetcher(
    private val api: KickApi,
) : ProfileFetcher {
    override val platform = Platform.KICK

    override suspend fun profile(token: String): Outcome<Account> =
        api.get(KickBase.API, "v1/user", token).decode(::accountOf)
}

private fun accountOf(root: JsonElement): Account {
    val user = root.decodeOrNull<UserJson>() ?: throw SerializationException("unexpected user reply")
    val id = user.id
    val name = user.username?.takeIf { it.isNotBlank() }
    if (id == null || name == null) throw SerializationException("user has no id or username")
    return Account(Platform.KICK, id.toString(), name, name, user.profilePic)
}
