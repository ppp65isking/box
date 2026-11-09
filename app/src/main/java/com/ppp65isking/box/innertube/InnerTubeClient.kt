package com.ppp65isking.box.innertube

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * One place for every InnerTube call. All calls go through [post]; callers
 * pass the client (WEB_REMIX for search/browse, VISIONOS for player) and
 * the endpoint-specific body fields.
 *
 * - No API key (none needed — see Clients.kt).
 * - visitorData is harvested from the first response and reused on every
 *   later call, including as the X-Goog-Visitor-Id header. Without it the
 *   player endpoint answers LOGIN_REQUIRED ("Sign in to confirm you're
 *   not a bot").
 * - Runs on the calling thread: callers must use Dispatchers.IO.
 */
class InnerTubeClient(
    private val okHttp: OkHttpClient,
    private val authStore: AuthStore,
) {
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var visitorData: String? = authStore.visitorData

    private fun clientContext(client: Clients.ClientDef): JsonObject = buildJsonObject {
        putJsonObject("client") {
            put("clientName", client.clientName)
            put("clientVersion", client.clientVersion)
            client.deviceMake?.let { put("deviceMake", it) }
            client.deviceModel?.let { put("deviceModel", it) }
            put("userAgent", client.userAgent)
            client.osName?.let { put("osName", it) }
            client.osVersion?.let { put("osVersion", it) }
            client.androidSdkVersion?.let { put("androidSdkVersion", it) }
            put("hl", "en")
            put("gl", "US")
            visitorData?.let { put("visitorData", it) }
        }
        putJsonObject("user") {
            put("lockedSafetyMode", false)
        }
    }

    fun post(
        endpoint: String,
        client: Clients.ClientDef,
        bodyExtra: JsonObjectBuilder.() -> Unit = {},
    ): JsonObject {
        val body = buildJsonObject {
            put("context", clientContext(client))
            bodyExtra()
        }
        val request = Request.Builder()
            .url("https://music.youtube.com/youtubei/v1/$endpoint?prettyPrint=false")
            .addHeader("Content-Type", "application/json")
            .addHeader("User-Agent", client.userAgent)
            .addHeader("X-YouTube-Client-Name", client.clientId.toString())
            .addHeader("X-YouTube-Client-Version", client.clientVersion)
            .addHeader("X-Goog-Api-Format-Version", "2")
            .apply { visitorData?.let { addHeader("X-Goog-Visitor-Id", it) } }
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        okHttp.newCall(request).execute().use { response ->
            val parsed = json.parseToJsonElement(
                response.body?.string().orEmpty(),
            ).jsonObject
            parsed["responseContext"]
                ?.jsonObject
                ?.get("visitorData")
                ?.jsonPrimitive
                ?.contentOrNull
                ?.let { fresh ->
                    if (fresh != visitorData) {
                        visitorData = fresh
                        authStore.visitorData = fresh
                    }
                }
            return parsed
        }
    }

    /**
     * Bootstrap visitorData before any player call. On a fresh install it
     * is null, and a player call sent without it answers LOGIN_REQUIRED.
     * A cheap `guide` call harvests it (post() stores it from every
     * responseContext); `browse` is the fallback. Best-effort: if both
     * fail, the player call still runs and StreamResolver retries once
     * after the harvest.
     */
    fun ensureVisitorData() {
        if (visitorData != null) return
        runCatching { post("guide", Clients.WEB_REMIX) }
        if (visitorData == null) {
            runCatching {
                post("browse", Clients.WEB_REMIX) { put("browseId", "FEmusic_home") }
            }
        }
    }

    /** Search. `filter` is one of [SearchParams] (null = unfiltered). */
    fun search(query: String, filter: String? = null): JsonObject =
        post("search", Clients.WEB_REMIX) {
            put("query", query)
            filter?.let { put("params", it) }
        }

    /** Search-filter params, copied from ytmusicapi 1.12.3 parsers/search.py. */
    object SearchParams {
        const val SONGS = "EgWKAQIIAWoMEA4QChADEAQQCRAF"
        const val VIDEOS = "EgWKAQIQAWoMEA4QChADEAQQCRAF"
        const val ALBUMS = "EgWKAQIYAWoMEA4QChADEAQQCRAF"
        const val ARTISTS = "EgWKAQIgAWoMEA4QChADEAQQCRAF"
        const val PLAYLISTS = "Eg-KAQwIABAAGAAgACgBMABqChAEEAMQCRAFEAo%3D"
        const val PODCASTS = "EgWKAQJQAWoMEA4QChADEAQQCRAF"
        const val EPISODES = "EgWKAQJIAWoMEA4QChADEAQQCRAF"
    }
}
