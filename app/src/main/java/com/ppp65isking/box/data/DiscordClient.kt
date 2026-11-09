package com.ppp65isking.box.data

import com.ppp65isking.box.model.DiscordChannel
import com.ppp65isking.box.model.DiscordGuild
import com.ppp65isking.box.model.DiscordMessage
import com.ppp65isking.box.model.DiscordUser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Discord REST API behind a USER OAuth2 sign-in — read-only, no bot token.
 *
 * What works with a user token: profile, server list, and channel lists
 * for servers the user is in. What does NOT reliably work: reading
 * channel messages. Discord's `messages.read` scope is for local RPC
 * server access only (per Discord's own docs), and a user token calling
 * the HTTP messages endpoint is refused by Discord — that is a Discord
 * rule, not a Box bug. [messages] therefore returns [Result.Restricted]
 * on 401/403 and the screen explains it in plain text instead of
 * showing an empty list. Sending messages is not possible with a user
 * token at all, so Box never tries. Callers must run these on
 * Dispatchers.IO.
 */
class DiscordClient(private val okHttp: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    sealed interface Result<out T> {
        data class Ok<T>(val value: T) : Result<T>
        /** The token is dead (revoked/expired beyond refresh) — sign in again. */
        data object Unauthorized : Result<Nothing>
        /** Discord refused this call for this kind of sign-in. */
        data class Restricted(val message: String) : Result<Nothing>
        data class Error(val message: String) : Result<Nothing>
    }

    private fun get(url: String, token: String): okhttp3.Response {
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .build()
        return okHttp.newCall(request).execute()
    }

    private fun <T> runGet(
        url: String,
        token: String,
        parse: (kotlinx.serialization.json.JsonElement) -> T,
    ): Result<T> {
        return runCatching {
            get(url, token).use { response ->
                if (response.code == 401) return Result.Unauthorized
                if (response.code == 403) {
                    return Result.Restricted(
                        "Discord refused this for a user sign-in.",
                    )
                }
                if (!response.isSuccessful) {
                    return Result.Error("Discord answered ${response.code}")
                }
                Result.Ok(
                    parse(
                        json.parseToJsonElement(response.body?.string().orEmpty()),
                    ),
                )
            }
        }.getOrElse { e -> Result.Error(e.message ?: "Network error") }
    }

    fun me(token: String): Result<DiscordUser> =
        runGet("https://discord.com/api/v10/users/@me", token) { element ->
            val root = element.jsonObject
            val id = root["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val avatar = root["avatar"]?.jsonPrimitive?.contentOrNull
            DiscordUser(
                id = id,
                username = root["username"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                displayName = root["global_name"]?.jsonPrimitive?.contentOrNull,
                email = root["email"]?.jsonPrimitive?.contentOrNull,
                avatarUrl = avatar?.let {
                    "https://cdn.discordapp.com/avatars/$id/$it.png"
                },
            )
        }

    fun guilds(token: String): Result<List<DiscordGuild>> =
        runGet("https://discord.com/api/v10/users/@me/guilds", token) { element ->
            element.jsonArray.mapNotNull { item ->
                runCatching {
                    val obj = item.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val icon = obj["icon"]?.jsonPrimitive?.contentOrNull
                    DiscordGuild(
                        id = id,
                        name = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        iconUrl = icon?.let {
                            "https://cdn.discordapp.com/icons/$id/$it.png"
                        },
                    )
                }.getOrNull()
            }
        }

    /** Text (type 0) and announcement (type 5) channels, in server order. */
    fun channels(token: String, guildId: String): Result<List<DiscordChannel>> =
        runGet("https://discord.com/api/v10/guilds/$guildId/channels", token) { element ->
            element.jsonArray.mapNotNull { item ->
                runCatching {
                    val obj = item.jsonObject
                    val type = obj["type"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                    if (type != 0 && type != 5) return@mapNotNull null
                    DiscordChannel(
                        id = obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        name = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        type = type,
                        position = obj["position"]?.jsonPrimitive?.intOrNull ?: 0,
                    )
                }.getOrNull()
            }.sortedBy { it.position }
        }

    /**
     * Newest messages in a channel — best effort (see the class doc).
     * A 401/403 from Discord comes back as [Result.Restricted], never as
     * an empty list, so the screen can say why instead of faking it.
     */
    fun messages(token: String, channelId: String): Result<List<DiscordMessage>> =
        runGet(
            "https://discord.com/api/v10/channels/$channelId/messages?limit=25",
            token,
        ) { element ->
            element.jsonArray.mapNotNull { item ->
                runCatching {
                    val obj = item.jsonObject
                    val author = obj["author"]?.jsonObject
                    val authorId = author?.get("id")?.jsonPrimitive?.contentOrNull.orEmpty()
                    val authorAvatar = author?.get("avatar")?.jsonPrimitive?.contentOrNull
                    DiscordMessage(
                        id = obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        content = obj["content"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        authorName = author?.get("global_name")?.jsonPrimitive?.contentOrNull
                            ?: author?.get("username")?.jsonPrimitive?.contentOrNull.orEmpty(),
                        authorAvatarUrl = authorAvatar?.let {
                            "https://cdn.discordapp.com/avatars/$authorId/$it.png"
                        },
                        timestamp = obj["timestamp"]?.jsonPrimitive?.contentOrNull,
                    )
                }.getOrNull()
            }
        }
}
