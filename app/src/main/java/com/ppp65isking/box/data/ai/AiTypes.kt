package com.ppp65isking.box.data.ai

/** One AI job Box can ask for. Every feature calls
 *  [AiRouter.complete] and never talks to a model directly — that is
 *  what makes adding a leg later a one-file change. */
sealed interface AiTask {
    /** Summarize notification lines ("App: title — text") into 1–3 bullets. */
    data class Summarize(val lines: List<String>) : AiTask

    /** Draft short replies to a chat (oldest first). */
    data class ReplyDraft(val messages: List<ChatMessage>) : AiTask
}

data class ChatMessage(val text: String, val isOutgoing: Boolean)

/** What came back, and from where — the engine label is shown in the
 *  UI ("AI: Nano") so the fallback is visible during testing. */
data class AiResult(val text: String, val engineLabel: String, val leg: AiLeg)

/** One leg of the fallback chain, in router order. */
enum class AiLeg { NANO, LITERT, GROQ, GEMINI_CLOUD, OPENROUTER, CLASSIC, PLAIN }

/** How the router picks legs. Auto = the full chain; forcing one leg
 *  still falls back to Classic then Plain, never a dead button. */
enum class AiMode { AUTO, NANO, LITERT, GROQ, GEMINI_CLOUD, OPENROUTER, CLASSIC }

interface AiEngine {
    val leg: AiLeg
    val label: String
    suspend fun available(): Boolean

    /** The answer, or null when this leg can't do the job (the router
     *  moves on). Throw [RateLimitedException] for a provider rate
     *  limit; any other throw is treated as a plain failure. */
    suspend fun run(task: AiTask): String?
}

/** A leg that hit a provider rate limit — the router cools it down
 *  instead of hammering it. */
class RateLimitedException(message: String) : Exception(message)

/** Prompt text for the LLM legs (Nano, LiteRT, cloud). Kept short:
 *  Nano's context is under 4000 tokens and the LiteRT model has a
 *  4096-token context, so the input is capped. */
internal fun AiTask.toPrompt(): String? = when (this) {
    is AiTask.Summarize -> {
        val body = lines.joinToString("\n").take(MAX_PROMPT_CHARS)
        if (body.isBlank()) null
        else "Summarize these notifications in 1-3 short bullet points. " +
            "Start each bullet with '• '. Keep the app name in each bullet. " +
            "Skip duplicates and system status noise.\n\n$body"
    }
    is AiTask.ReplyDraft -> {
        val convo = messages.takeLast(8)
            .joinToString("\n") { if (it.isOutgoing) "You: ${it.text}" else "Them: ${it.text}" }
            .take(MAX_PROMPT_CHARS)
        if (convo.isBlank()) null
        else "Suggest 3 short casual replies to this conversation. Each reply " +
            "under 12 words. Reply with only the 3 replies, one per line, " +
            "no numbering or quotes.\n\n$convo"
    }
}

/** Roughly 1500 words / 2000 tokens — inside Nano's 4000-token
 *  context and the LiteRT model's 4096-token context. */
internal const val MAX_PROMPT_CHARS = 6000

/** Split an LLM reply draft (one reply per line, possibly numbered
 *  or bulleted despite the prompt) into clean reply strings. */
internal fun parseReplies(text: String): List<String> = text.lines()
    .map {
        it.trim()
            .replace(Regex("^[\\d.•\\-*\\)\\s]+"), "")
            .trim()
            .trim('"', '\'')
    }
    .filter { it.isNotBlank() }
    .distinct()
    .take(3)
