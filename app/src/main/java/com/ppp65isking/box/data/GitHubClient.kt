package com.ppp65isking.box.data

import com.ppp65isking.box.model.GitHubRepo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/** Unauthenticated public GitHub REST API — no account, no token.
 *  Limit is 60 requests/hour per IP; a 403 with rate-limit headers means
 *  wait it out. Callers must run this on Dispatchers.IO. */
class GitHubClient(private val okHttp: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    sealed interface Result {
        data class Ok(val repos: List<GitHubRepo>, val remaining: Int?) : Result
        data class RateLimited(val resetsAtEpochSeconds: Long?) : Result
        data class Error(val message: String) : Result
    }

    fun searchRepos(query: String): Result {
        val request = Request.Builder()
            .url("https://api.github.com/search/repositories?q=${encode(query)}&sort=stars&per_page=20")
            .addHeader("Accept", "application/vnd.github+json")
            .addHeader("User-Agent", "Box-App")
            .build()
        return runCatching {
            okHttp.newCall(request).execute().use { response ->
                val remaining = response.header("X-RateLimit-Remaining")?.toIntOrNull()
                if (response.code == 403) {
                    val reset = response.header("X-RateLimit-Reset")?.toLongOrNull()
                    return Result.RateLimited(reset)
                }
                if (!response.isSuccessful) {
                    return Result.Error("GitHub answered ${response.code}")
                }
                val body = json.parseToJsonElement(
                    response.body?.string().orEmpty(),
                ).jsonObject
                val repos = body["items"]?.jsonArray.orEmpty().mapNotNull { item ->
                    runCatching { parseRepo(item.jsonObject) }.getOrNull()
                }
                Result.Ok(repos, remaining)
            }
        }.getOrElse { e -> Result.Error(e.message ?: "Network error") }
    }

    fun repo(fullName: String): Result {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$fullName")
            .addHeader("Accept", "application/vnd.github+json")
            .addHeader("User-Agent", "Box-App")
            .build()
        return runCatching {
            okHttp.newCall(request).execute().use { response ->
                if (response.code == 403) {
                    return Result.RateLimited(
                        response.header("X-RateLimit-Reset")?.toLongOrNull(),
                    )
                }
                if (!response.isSuccessful) {
                    return Result.Error("GitHub answered ${response.code}")
                }
                val repo = parseRepo(
                    json.parseToJsonElement(
                        response.body?.string().orEmpty(),
                    ).jsonObject,
                )
                Result.Ok(listOf(repo), response.header("X-RateLimit-Remaining")?.toIntOrNull())
            }
        }.getOrElse { e -> Result.Error(e.message ?: "Network error") }
    }

    private fun parseRepo(obj: kotlinx.serialization.json.JsonObject): GitHubRepo {
        return GitHubRepo(
            fullName = obj["full_name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            description = obj["description"]?.jsonPrimitive?.contentOrNull,
            stars = obj["stargazers_count"]?.jsonPrimitive?.intOrNull ?: 0,
            language = obj["language"]?.jsonPrimitive?.contentOrNull,
            htmlUrl = obj["html_url"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        )
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8)
}
