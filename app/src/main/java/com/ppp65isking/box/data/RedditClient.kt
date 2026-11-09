package com.ppp65isking.box.data

import com.ppp65isking.box.model.RedditPost
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Reddit content — read-only, no user account.
 *  Subreddit feeds use the public .json endpoints (work anonymously).
 *  Search is different: reddit.com/search.json 403s anonymous requests,
 *  so search goes through app-only OAuth (oauth.reddit.com) with an
 *  "installed app" client ID — free, no user login, no secret.
 *  The client ID lives in [RedditAuthStore]; until Anthony pastes one in
 *  (More → Reddit app ID), search explains the 2-minute setup instead of
 *  showing a bare 403. Callers must run this on Dispatchers.IO. */
class RedditClient(
    private val okHttp: OkHttpClient,
    private val authStore: RedditAuthStore,
) {

    private val json = Json { ignoreUnknownKeys = true }

    sealed interface Result {
        data class Ok(val posts: List<RedditPost>) : Result
        data class Error(val message: String) : Result
    }

    fun popular(): Result = fetch("https://www.reddit.com/r/popular/.json?limit=25")

    fun subreddit(name: String): Result =
        fetch("https://www.reddit.com/r/${encode(name)}/.json?limit=25")

    fun search(query: String): Result {
        val clientId = authStore.clientId?.takeIf { it.isNotBlank() }
            ?: return Result.Error(
                "Reddit search needs a free app ID first — create an \"installed app\" " +
                    "at reddit.com/prefs/apps, then paste its client ID in More → Setup.",
            )
        val token = accessToken(clientId)
            ?: return Result.Error("Reddit wouldn't hand out a search token — try again in a bit.")
        val request = Request.Builder()
            .url("https://oauth.reddit.com/search?q=${encode(query)}&sort=relevance&limit=25")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("User-Agent", USER_AGENT)
            .build()
        return runCatching {
            okHttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.Error("Reddit answered ${response.code}")
                }
                parseListing(response.body?.string().orEmpty())
            }
        }.getOrElse { e -> Result.Error(e.message ?: "Network error") }
    }

    /** App-only OAuth: installed apps need only the client ID as the
     *  Basic-auth username (empty password), grant_type=client_credentials. */
    private fun accessToken(clientId: String): String? {
        val cached = authStore.accessToken
        if (cached != null && System.currentTimeMillis() < authStore.tokenExpiresAt) return cached
        val credentials = okhttp3.Credentials.basic(clientId, "")
        val body = "grant_type=client_credentials"
            .toRequestBody("application/x-www-form-urlencoded".toMediaType())
        val request = Request.Builder()
            .url("https://www.reddit.com/api/v1/access_token")
            .addHeader("Authorization", credentials)
            .addHeader("User-Agent", USER_AGENT)
            .post(body)
            .build()
        return runCatching {
            okHttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val root = json.parseToJsonElement(
                    response.body?.string().orEmpty(),
                ).jsonObject
                val token = root["access_token"]?.jsonPrimitive?.contentOrNull ?: return null
                val expiresIn = root["expires_in"]?.jsonPrimitive?.intOrNull ?: 3600
                authStore.accessToken = token
                authStore.tokenExpiresAt =
                    System.currentTimeMillis() + (expiresIn - 60) * 1_000L
                token
            }
        }.getOrNull()
    }

    private fun fetch(url: String): Result {
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", USER_AGENT)
            .build()
        return runCatching {
            okHttp.newCall(request).execute().use { response ->
                if (response.code == 429) {
                    return Result.Error("Reddit is rate-limiting anonymous browsing right now — try again in a bit.")
                }
                if (!response.isSuccessful) {
                    return Result.Error("Reddit answered ${response.code}")
                }
                parseListing(response.body?.string().orEmpty())
            }
        }.getOrElse { e -> Result.Error(e.message ?: "Network error") }
    }

    private fun parseListing(body: String): Result {
        val root = json.parseToJsonElement(body).jsonObject
        val children = root["data"]?.jsonObject
            ?.get("children")?.jsonArray.orEmpty()
        val posts = children.mapNotNull { child ->
            runCatching { parsePost(child.jsonObject["data"]?.jsonObject) }.getOrNull()
        }
        return Result.Ok(posts)
    }

    private fun parsePost(data: kotlinx.serialization.json.JsonObject?): RedditPost? {
        if (data == null) return null
        val title = data["title"]?.jsonPrimitive?.contentOrNull ?: return null
        val thumb = data["thumbnail"]?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.startsWith("http") }
        return RedditPost(
            title = title,
            subreddit = data["subreddit"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            author = data["author"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            score = data["score"]?.jsonPrimitive?.intOrNull ?: 0,
            comments = data["num_comments"]?.jsonPrimitive?.intOrNull ?: 0,
            url = data["url"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            thumbnail = thumb,
            permalink = "https://www.reddit.com" +
                (data["permalink"]?.jsonPrimitive?.contentOrNull.orEmpty()),
        )
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8)

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 9 Pro) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/126.0.0.0 Mobile Safari/537.36"
    }
}
