package com.ppp65isking.box.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Free cloud legs. Each needs the user's own free key (Groq first —
 * biggest free quota, no card; Gemini second; OpenRouter as spare).
 * No key = the leg isn't in the chain. Nothing here can bill anyone:
 * none of the three free tiers takes a card, and a spent quota
 * answers 429 (OpenRouter: 402), never a charge.
 *
 * NOT in the free chain, by design: Muse (pay-as-you-go, no free
 * tier), Together AI (free tier ended), Cerebras (card-gated trial),
 * Hugging Face free serverless (credits gone), GitHub Models (status
 * unverified). See ai-in-box-research.md §9.3.
 */
abstract class CloudEngine(
    protected val okHttp: OkHttpClient,
    protected val keys: AiKeyStore,
) : AiEngine {

    private val json = Json { ignoreUnknownKeys = true }

    /** POST JSON, returning the raw reply body. 429/402 become
     *  [RateLimitedException] so the router cools the leg down. */
    protected suspend fun post(
        url: String,
        headers: Map<String, String>,
        body: String,
    ): String = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(url)
        headers.forEach { (name, value) -> builder.addHeader(name, value) }
        val request = builder
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        okHttp.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            when (response.code) {
                429 -> throw RateLimitedException("Rate limit — try again later.")
                402 -> throw RateLimitedException("Out of credits — try again later.")
                in 200..299 -> text
                401, 403 -> throw IOException(
                    "The key was refused — check it in Settings.",
                )
                else -> throw IOException("Cloud error (${response.code}).")
            }
        }
    }

    protected fun openAiBody(model: String, prompt: String): String =
        buildJsonObject {
            put("model", model)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    put("content", prompt)
                }
            }
            put("max_tokens", 512)
            put("temperature", 0.3)
        }.toString()

    protected fun parseOpenAiReply(text: String): String? = runCatching {
        json.parseToJsonElement(text)
            .jsonObject["choices"]?.jsonArray
            ?.firstOrNull()?.jsonObject?.get("message")?.jsonObject
            ?.get("content")?.jsonPrimitive?.content
            ?.takeIf { it.isNotBlank() }
    }.getOrNull()
}

/** Groq — best free cloud leg (30 RPM / 1,000 req/day, no card).
 *  OpenAI-compatible endpoint. */
class GroqEngine(okHttp: OkHttpClient, keys: AiKeyStore) :
    CloudEngine(okHttp, keys) {
    override val leg = AiLeg.GROQ
    override val label = "Groq cloud"

    override suspend fun available(): Boolean = !keys.groqKey.isNullOrBlank()

    override suspend fun run(task: AiTask): String? {
        val key = keys.groqKey ?: return null
        val prompt = task.toPrompt() ?: return null
        val raw = post(
            url = "https://api.groq.com/openai/v1/chat/completions",
            headers = mapOf("Authorization" to "Bearer $key"),
            body = openAiBody("openai/gpt-oss-20b", prompt),
        )
        return parseOpenAiReply(raw)
    }
}

/** Gemini API (AI Studio) — solid second leg, free tier, no card. */
class GeminiCloudEngine(okHttp: OkHttpClient, keys: AiKeyStore) :
    CloudEngine(okHttp, keys) {
    override val leg = AiLeg.GEMINI_CLOUD
    override val label = "Gemini cloud"

    override suspend fun available(): Boolean = !keys.geminiKey.isNullOrBlank()

    override suspend fun run(task: AiTask): String? {
        val key = keys.geminiKey ?: return null
        val prompt = task.toPrompt() ?: return null
        val body = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    putJsonArray("parts") {
                        addJsonObject { put("text", prompt) }
                    }
                }
            }
        }.toString()
        val raw = post(
            url = "https://generativelanguage.googleapis.com/v1beta/models/" +
                "gemini-2.5-flash:generateContent?key=$key",
            headers = mapOf("x-goog-api-key" to key),
            body = body,
        )
        return runCatching {
            Json { ignoreUnknownKeys = true }
                .parseToJsonElement(raw)
                .jsonObject["candidates"]?.jsonArray
                ?.firstOrNull()?.jsonObject?.get("content")?.jsonObject
                ?.get("parts")?.jsonArray
                ?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}

/** OpenRouter — spare leg only. The `:free` models / `openrouter/free`
 *  router is 50 requests/day unfunded and the free lineup rotates, so
 *  it sits last among the cloud legs. */
class OpenRouterEngine(okHttp: OkHttpClient, keys: AiKeyStore) :
    CloudEngine(okHttp, keys) {
    override val leg = AiLeg.OPENROUTER
    override val label = "OpenRouter free"

    override suspend fun available(): Boolean = !keys.openRouterKey.isNullOrBlank()

    override suspend fun run(task: AiTask): String? {
        val key = keys.openRouterKey ?: return null
        val prompt = task.toPrompt() ?: return null
        val raw = post(
            url = "https://openrouter.ai/api/v1/chat/completions",
            headers = mapOf(
                "Authorization" to "Bearer $key",
                "HTTP-Referer" to "https://github.com/ppp65isking/box",
                "X-Title" to "Box",
            ),
            body = openAiBody("openrouter/free", prompt),
        )
        return parseOpenAiReply(raw)
    }
}
