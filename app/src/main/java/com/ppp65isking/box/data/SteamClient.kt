package com.ppp65isking.box.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

data class SteamPlayer(
    val steamId: String,
    val name: String,
    val avatarUrl: String?,
    val profileUrl: String?,
    /** 0 offline, 1 online, 2 busy, 3 away, 4 snooze, 5 looking to trade, 6 looking to play. */
    val state: Int,
    /** Name of the game being played right now, or null. */
    val currentGame: String?,
)

data class SteamGame(
    val appId: Long,
    val name: String,
    val playtimeMinutes: Int,
    val iconUrl: String?,
)

/**
 * Steam Web API behind the user's own API key — read-only.
 *
 * Profile, owned games, and friends (with their online status and what
 * they're playing). The key is per-user and lives in [SteamStore]; it
 * is never shipped in the app. A private profile returns empty games /
 * friends lists rather than an error — Steam just has nothing to show.
 * Callers must run these on Dispatchers.IO.
 */
class SteamClient(private val okHttp: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    sealed interface Result<out T> {
        data class Ok<T>(val value: T) : Result<T>
        /** The API key is wrong or missing. */
        data object Unauthorized : Result<Nothing>
        /** Steam refused the request (private profile, key not allowed). */
        data object Forbidden : Result<Nothing>
        data class Error(val message: String) : Result<Nothing>
    }

    private fun <T> runGet(
        url: String,
        parse: (kotlinx.serialization.json.JsonObject) -> T,
    ): Result<T> {
        return runCatching {
            val request = Request.Builder().url(url).build()
            okHttp.newCall(request).execute().use { response ->
                if (response.code == 401) return Result.Unauthorized
                if (response.code == 403) return Result.Forbidden
                if (!response.isSuccessful) {
                    return Result.Error("Steam answered ${response.code}")
                }
                val root = json.parseToJsonElement(
                    response.body?.string().orEmpty(),
                ).jsonObject
                Result.Ok(parse(root))
            }
        }.getOrElse { Result.Error("Network error") }
    }

    /** The signed-in player's profile. Error when Steam has no player
     *  for this SteamID — usually a mistyped SteamID64. */
    fun playerSummary(apiKey: String, steamId: String): Result<SteamPlayer> {
        return when (
            val result = runGet(playerSummariesUrl(apiKey, listOf(steamId))) { root ->
                val players = root["response"]?.jsonObject
                    ?.get("players")?.jsonArray.orEmpty()
                parsePlayer(players.firstOrNull()?.jsonObject)
            }
        ) {
            is Result.Ok -> result.value?.let { Result.Ok(it) }
                ?: Result.Error(
                    "Steam couldn't find that SteamID — check it and try again.",
                )
            is Result.Unauthorized -> Result.Unauthorized
            is Result.Forbidden -> Result.Forbidden
            is Result.Error -> result
        }
    }

    /** Owned games, most-played first. Empty when the profile is private. */
    fun ownedGames(apiKey: String, steamId: String): Result<List<SteamGame>> =
        runGet(
            "https://api.steampowered.com/IPlayerService/GetOwnedGames/v0001/" +
                "?key=${encode(apiKey)}&steamid=${encode(steamId)}" +
                "&format=json&include_appinfo=true",
        ) { root ->
            root["response"]?.jsonObject
                ?.get("games")?.jsonArray.orEmpty().mapNotNull { item ->
                    runCatching {
                        val obj = item.jsonObject
                        val appId = obj["appid"]?.jsonPrimitive?.longOrNull
                            ?: return@mapNotNull null
                        val iconHash = obj["img_icon_url"]?.jsonPrimitive?.contentOrNull
                        SteamGame(
                            appId = appId,
                            name = obj["name"]?.jsonPrimitive?.contentOrNull
                                ?: "Unknown game",
                            playtimeMinutes = obj["playtime_forever"]
                                ?.jsonPrimitive?.intOrNull ?: 0,
                            iconUrl = iconHash?.takeIf { it.isNotBlank() }?.let {
                                "https://media.steampowered.com/steamcommunity/" +
                                    "public/images/apps/$appId/$it.jpg"
                            },
                        )
                    }.getOrNull()
                }.sortedByDescending { it.playtimeMinutes }
        }

    /** Friends with their online status and current game. Best-effort:
     *  a private friends list comes back empty. */
    fun friends(apiKey: String, steamId: String): Result<List<SteamPlayer>> {
        val ids = when (val list = runGet(
            "https://api.steampowered.com/ISteamUser/GetFriendList/v0001/" +
                "?key=${encode(apiKey)}&steamid=${encode(steamId)}" +
                "&relationship=friend",
        ) { root ->
            root["friendslist"]?.jsonObject
                ?.get("friends")?.jsonArray.orEmpty().mapNotNull {
                    it.jsonObject["steamid"]?.jsonPrimitive?.contentOrNull
                }
        }) {
            is Result.Ok -> list.value
            is Result.Unauthorized -> return Result.Unauthorized
            is Result.Forbidden -> return Result.Forbidden
            is Result.Error -> return list
        }
        if (ids.isEmpty()) return Result.Ok(emptyList())
        val players = mutableListOf<SteamPlayer>()
        ids.chunked(100).forEach { chunk ->
            when (val summaries = runGet(playerSummariesUrl(apiKey, chunk)) { root ->
                root["response"]?.jsonObject
                    ?.get("players")?.jsonArray.orEmpty().mapNotNull {
                        parsePlayer(it.jsonObject)
                    }
            }) {
                is Result.Ok -> players += summaries.value
                is Result.Unauthorized -> return Result.Unauthorized
                is Result.Forbidden -> return Result.Forbidden
                is Result.Error -> return summaries
            }
        }
        return Result.Ok(players.sortedBy { it.name.lowercase() })
    }

    private fun playerSummariesUrl(apiKey: String, steamIds: List<String>): String =
        "https://api.steampowered.com/ISteamUser/GetPlayerSummaries/v0002/" +
            "?key=${encode(apiKey)}&steamids=${encode(steamIds.joinToString(","))}"

    private fun parsePlayer(obj: kotlinx.serialization.json.JsonObject?): SteamPlayer? {
        if (obj == null) return null
        val steamId = obj["steamid"]?.jsonPrimitive?.contentOrNull ?: return null
        val name = obj["personaname"]?.jsonPrimitive?.contentOrNull ?: return null
        return SteamPlayer(
            steamId = steamId,
            name = name,
            avatarUrl = obj["avatarfull"]?.jsonPrimitive?.contentOrNull
                ?: obj["avatarmedium"]?.jsonPrimitive?.contentOrNull
                ?: obj["avatar"]?.jsonPrimitive?.contentOrNull,
            profileUrl = obj["profileurl"]?.jsonPrimitive?.contentOrNull,
            state = obj["personastate"]?.jsonPrimitive?.intOrNull ?: 0,
            currentGame = obj["gameextrainfo"]?.jsonPrimitive?.contentOrNull,
        )
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8)
}
