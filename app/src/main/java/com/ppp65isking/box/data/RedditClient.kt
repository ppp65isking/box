package com.ppp65isking.box.data

import com.ppp65isking.box.model.RedditPost
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/** Public Reddit .json endpoints — read-only, no account, no auth.
 *  Reddit rate-limits anonymous .json calls, so a browser-like
 *  User-Agent header is required and errors are shown honestly.
 *  Callers must run this on Dispatchers.IO. */
class RedditClient(private val okHttp: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    sealed interface Result {
        data class Ok(val posts: List<RedditPost>) : Result
        data class Error(val message: String) : Result
    }

    fun popular(): Result = fetch("https://www.reddit.com/r/popular/.json?limit=25")

    fun subreddit(name: String): Result =
        fetch("https://www.reddit.com/r/${encode(name)}/.json?limit=25")

    fun search(query: String): Result =
        fetch("https://www.reddit.com/search.json?q=${encode(query)}&sort=relevance&limit=25")

    private fun fetch(url: String): Result {
        val request = Request.Builder()
            .url(url)
            .addHeader(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; Pixel 9 Pro) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/126.0.0.0 Mobile Safari/537.36",
            )
            .build()
        return runCatching {
            okHttp.newCall(request).execute().use { response ->
                if (response.code == 429) {
                    return Result.Error("Reddit is rate-limiting anonymous browsing right now — try again in a bit.")
                }
                if (!response.isSuccessful) {
                    return Result.Error("Reddit answered ${response.code}")
                }
                val root = json.parseToJsonElement(
                    response.body?.string().orEmpty(),
                ).jsonObject
                val children = root["data"]?.jsonObject
                    ?.get("children")?.jsonArray.orEmpty()
                val posts = children.mapNotNull { child ->
                    runCatching { parsePost(child.jsonObject["data"]?.jsonObject) }.getOrNull()
                }
                Result.Ok(posts)
            }
        }.getOrElse { e -> Result.Error(e.message ?: "Network error") }
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
}
