package com.ppp65isking.box.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder

/**
 * Pollinations AI — free public text generation, no API key, no
 * sign-up (text.pollinations.ai). Verified live 2026-11-09: a GET
 * returns plain text, HTTP 200, no key anywhere. The free tier is
 * 1 request per 45 seconds per IP — the server answers 402 when
 * that is exceeded, so a 402/429 becomes a [RateLimitedException]
 * and the router cools the leg down and moves on to Smart Reply.
 *
 * GET with the prompt in the URL (the documented no-key form), so
 * the prompt is capped shorter than the other legs to keep the URL
 * sane. Nothing here can bill anyone and there is nothing to paste
 * in Settings — this is the only cloud leg in the no-signup-only
 * chain (see AiRouter).
 */
class PollinationsEngine(private val okHttp: OkHttpClient) : AiEngine {
    override val leg = AiLeg.POLLINATIONS
    override val label = "Pollinations"

    override suspend fun available(): Boolean = true

    override suspend fun run(task: AiTask): String? {
        val prompt = task.toPrompt()?.take(MAX_POLL_PROMPT_CHARS) ?: return null
        // URLEncoder puts "+" for spaces — in a path segment that is
        // a literal plus, so swap it for %20.
        val encoded = URLEncoder.encode(prompt, Charsets.UTF_8.name())
            .replace("+", "%20")
        val url = "https://text.pollinations.ai/$encoded?model=openai"
        return withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).get().build()
            okHttp.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                when (response.code) {
                    429, 402 -> throw RateLimitedException(
                        "Pollinations free limit — try again in a minute.",
                    )
                    in 200..299 -> text.trim().takeIf { it.isNotBlank() }
                    else -> throw IOException("Pollinations error (${response.code}).")
                }
            }
        }
    }

    companion object {
        /** Shorter than the shared 6000-char cap: the prompt rides
         *  in the URL on this leg. */
        private const val MAX_POLL_PROMPT_CHARS = 4000
    }
}
