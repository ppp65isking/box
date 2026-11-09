package com.ppp65isking.box.data

import com.ppp65isking.box.model.SpotifyNowPlaying
import com.ppp65isking.box.model.SpotifyPlaylist
import com.ppp65isking.box.model.SpotifyProfile
import com.ppp65isking.box.model.SpotifyTrack
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Spotify Web API behind a user OAuth sign-in — read-only except for
 * play/pause on the user's own active player.
 *
 * Library + playlists + currently playing work on any account. Playback
 * control is different: Spotify only lets Premium accounts start/pause
 * playback through the Web API, and only when a Spotify device is already
 * playing (or open). A 403 from play/pause means "needs Premium" and a
 * 404 means "no active device" — both are surfaced as plain text, not
 * as a generic error. Callers must run these on Dispatchers.IO.
 */
class SpotifyClient(private val okHttp: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    sealed interface Result<out T> {
        data class Ok<T>(val value: T) : Result<T>
        /** The token is dead (revoked/expired beyond refresh) — sign in again. */
        data object Unauthorized : Result<Nothing>
        /** Play/pause refused: Premium or an active device is missing. */
        data class PlaybackUnavailable(val message: String) : Result<Nothing>
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
        parse: (kotlinx.serialization.json.JsonObject) -> T,
    ): Result<T> {
        return runCatching {
            get(url, token).use { response ->
                if (response.code == 401) return Result.Unauthorized
                if (!response.isSuccessful) {
                    return Result.Error("Spotify answered ${response.code}")
                }
                val root = json.parseToJsonElement(
                    response.body?.string().orEmpty(),
                ).jsonObject
                Result.Ok(parse(root))
            }
        }.getOrElse { e -> Result.Error(e.message ?: "Network error") }
    }

    fun me(token: String): Result<SpotifyProfile> =
        runGet("https://api.spotify.com/v1/me", token) { root ->
            SpotifyProfile(
                displayName = root["display_name"]?.jsonPrimitive?.contentOrNull,
                email = root["email"]?.jsonPrimitive?.contentOrNull,
                product = root["product"]?.jsonPrimitive?.contentOrNull,
                imageUrl = root["images"]?.jsonArray
                    ?.firstOrNull()?.jsonObject
                    ?.get("url")?.jsonPrimitive?.contentOrNull,
            )
        }

    fun playlists(token: String): Result<List<SpotifyPlaylist>> =
        runGet("https://api.spotify.com/v1/me/playlists?limit=20", token) { root ->
            root["items"]?.jsonArray.orEmpty().mapNotNull { item ->
                runCatching {
                    val obj = item.jsonObject
                    SpotifyPlaylist(
                        id = obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        name = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        trackCount = obj["tracks"]?.jsonObject
                            ?.get("total")?.jsonPrimitive?.intOrNull ?: 0,
                        imageUrl = obj["images"]?.jsonArray
                            ?.firstOrNull()?.jsonObject
                            ?.get("url")?.jsonPrimitive?.contentOrNull,
                        owner = obj["owner"]?.jsonObject
                            ?.get("display_name")?.jsonPrimitive?.contentOrNull,
                    )
                }.getOrNull()
            }
        }

    fun savedTracks(token: String): Result<List<SpotifyTrack>> =
        runGet("https://api.spotify.com/v1/me/tracks?limit=20", token) { root ->
            root["items"]?.jsonArray.orEmpty().mapNotNull { item ->
                runCatching {
                    parseTrack(item.jsonObject["track"]?.jsonObject)
                }.getOrNull()
            }
        }

    /** Null when nothing is playing (Spotify answers 204). */
    fun currentlyPlaying(token: String): Result<SpotifyNowPlaying?> {
        return runCatching {
            get("https://api.spotify.com/v1/me/player/currently-playing", token)
                .use { response ->
                    if (response.code == 401) return Result.Unauthorized
                    if (response.code == 204) return Result.Ok(null)
                    if (!response.isSuccessful) {
                        return Result.Error("Spotify answered ${response.code}")
                    }
                    val root = json.parseToJsonElement(
                        response.body?.string().orEmpty(),
                    ).jsonObject
                    val item = root["item"]?.jsonObject ?: return Result.Ok(null)
                    Result.Ok(
                        SpotifyNowPlaying(
                            track = parseTrack(item),
                            isPlaying = root["is_playing"]?.jsonPrimitive?.booleanOrNull
                                ?: false,
                            progressMs = root["progress_ms"]?.jsonPrimitive?.longOrNull
                                ?: 0L,
                        ),
                    )
                }
        }.getOrElse { e -> Result.Error(e.message ?: "Network error") }
    }

    /** Resume playback on the user's active device. Premium-only. */
    fun play(token: String): Result<Unit> = playbackCall(
        "https://api.spotify.com/v1/me/player/play",
        token,
    )

    /** Pause playback on the user's active device. Premium-only. */
    fun pause(token: String): Result<Unit> = playbackCall(
        "https://api.spotify.com/v1/me/player/pause",
        token,
    )

    private fun playbackCall(url: String, token: String): Result<Unit> {
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .put("{}".toRequestBody("application/json".toMediaType()))
                .build()
            okHttp.newCall(request).execute().use { response ->
                if (response.code == 401) return Result.Unauthorized
                if (response.code == 403) {
                    return Result.PlaybackUnavailable(
                        "Spotify only lets Premium accounts control playback " +
                            "from another app.",
                    )
                }
                if (response.code == 404) {
                    return Result.PlaybackUnavailable(
                        "No active Spotify device — open Spotify on a phone, " +
                            "computer, or speaker first.",
                    )
                }
                if (!response.isSuccessful) {
                    return Result.Error("Spotify answered ${response.code}")
                }
                Result.Ok(Unit)
            }
        }.getOrElse { e -> Result.Error(e.message ?: "Network error") }
    }

    private fun parseTrack(obj: kotlinx.serialization.json.JsonObject?): SpotifyTrack? {
        if (obj == null) return null
        val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return null
        val artists = obj["artists"]?.jsonArray.orEmpty().mapNotNull {
            it.jsonObject["name"]?.jsonPrimitive?.contentOrNull
        }
        val album = obj["album"]?.jsonObject
        return SpotifyTrack(
            id = obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            name = name,
            artists = artists,
            albumName = album?.get("name")?.jsonPrimitive?.contentOrNull,
            imageUrl = album?.get("images")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("url")?.jsonPrimitive?.contentOrNull,
            durationMs = obj["duration_ms"]?.jsonPrimitive?.longOrNull ?: 0L,
        )
    }
}
