package com.ppp65isking.box.data.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

data class LegStatus(
    val leg: AiLeg,
    val label: String,
    val detail: String,
    val ready: Boolean,
)

/**
 * The AI router — one entry point, [complete]. It owns the ordered
 * leg list and the cooldown map, and every feature calls it instead of
 * talking to a model directly. Adding a leg later is a one-file
 * change: a new [AiEngine] plus one line here.
 *
 * Chain (Auto): Gemini Nano → downloaded Qwen model → Groq cloud →
 * Gemini cloud → OpenRouter free → ML Kit classic → plain list.
 * Availability is checked lazily, per call; a leg that fails or hits
 * a rate limit is cooled down (15 min for rate limits, 5 min for
 * other failures) instead of being hammered.
 */
class AiRouter(
    context: Context,
    okHttp: OkHttpClient,
    private val keyStore: AiKeyStore,
    private val modelManager: LiteRtModelManager,
) {
    val prefs = AiPrefs(context)

    val nanoEngine = NanoEngine()
    private val liteRtEngine = LiteRtEngine(context, modelManager)
    private val groqEngine = GroqEngine(okHttp, keyStore)
    private val geminiEngine = GeminiCloudEngine(okHttp, keyStore)
    private val openRouterEngine = OpenRouterEngine(okHttp, keyStore)
    private val classicEngine = ClassicEngine(context)
    private val plainEngine = PlainEngine()

    private val engines: Map<AiLeg, AiEngine> = mapOf(
        AiLeg.NANO to nanoEngine,
        AiLeg.LITERT to liteRtEngine,
        AiLeg.GROQ to groqEngine,
        AiLeg.GEMINI_CLOUD to geminiEngine,
        AiLeg.OPENROUTER to openRouterEngine,
        AiLeg.CLASSIC to classicEngine,
        AiLeg.PLAIN to plainEngine,
    )

    private val _lastEngine = MutableStateFlow<String?>(prefs.lastEngine.value)
    val lastEngine: StateFlow<String?> = _lastEngine.asStateFlow()

    /** Leg -> time (epoch ms) until which it is cooled down. In-memory
     *  only; a restart clears it. */
    private val cooldownUntil = mutableMapOf<AiLeg, Long>()

    /** One entry point. Tries each leg in order; on an error, a rate
     *  limit or an empty answer it moves to the next leg. Never throws
     *  and never returns an empty answer — Plain always answers. */
    suspend fun complete(task: AiTask): AiResult {
        for (leg in orderedLegs()) {
            if (System.currentTimeMillis() < (cooldownUntil[leg] ?: 0L)) continue
            val engine = engines[leg] ?: continue
            try {
                if (!engine.available()) continue
                val text = engine.run(task)?.trim()
                if (!text.isNullOrBlank()) {
                    prefs.setLastEngine(engine.label)
                    _lastEngine.value = engine.label
                    return AiResult(text, engine.label, leg)
                }
            } catch (e: RateLimitedException) {
                cooldownUntil[leg] = System.currentTimeMillis() + 15 * 60 * 1000L
            } catch (e: Exception) {
                cooldownUntil[leg] = System.currentTimeMillis() + 5 * 60 * 1000L
            }
        }
        // Plain always answers, so this is unreachable — kept as a guard.
        return AiResult("Nothing to show.", "List", AiLeg.PLAIN)
    }

    private fun orderedLegs(): List<AiLeg> = when (prefs.mode) {
        AiMode.AUTO -> listOf(
            AiLeg.NANO,
            AiLeg.LITERT,
            AiLeg.GROQ,
            AiLeg.GEMINI_CLOUD,
            AiLeg.OPENROUTER,
            AiLeg.CLASSIC,
            AiLeg.PLAIN,
        )
        AiMode.NANO -> listOf(AiLeg.NANO, AiLeg.CLASSIC, AiLeg.PLAIN)
        AiMode.LITERT -> listOf(AiLeg.LITERT, AiLeg.CLASSIC, AiLeg.PLAIN)
        AiMode.GROQ -> listOf(AiLeg.GROQ, AiLeg.CLASSIC, AiLeg.PLAIN)
        AiMode.GEMINI_CLOUD -> listOf(AiLeg.GEMINI_CLOUD, AiLeg.CLASSIC, AiLeg.PLAIN)
        AiMode.OPENROUTER -> listOf(AiLeg.OPENROUTER, AiLeg.CLASSIC, AiLeg.PLAIN)
        AiMode.CLASSIC -> listOf(AiLeg.CLASSIC, AiLeg.PLAIN)
    }

    /** Per-leg status lines for Settings → "AI model". */
    suspend fun legStatus(): List<LegStatus> = withContext(Dispatchers.Default) {
        listOf(
            LegStatus(
                AiLeg.NANO,
                "Gemini Nano",
                nanoEngine.statusText(),
                nanoEngine.available(),
            ),
            LegStatus(
                AiLeg.LITERT,
                "On-device model (Qwen 1.5B)",
                if (modelManager.isDownloaded()) "Downloaded — ready"
                else "Not downloaded (~1.6 GB)",
                liteRtEngine.available(),
            ),
            LegStatus(
                AiLeg.GROQ,
                "Groq cloud",
                if (!keyStore.groqKey.isNullOrBlank()) "Key saved"
                else "No key yet — add one below",
                groqEngine.available(),
            ),
            LegStatus(
                AiLeg.GEMINI_CLOUD,
                "Gemini cloud",
                if (!keyStore.geminiKey.isNullOrBlank()) "Key saved"
                else "No key yet — add one below",
                geminiEngine.available(),
            ),
            LegStatus(
                AiLeg.OPENROUTER,
                "OpenRouter free",
                if (!keyStore.openRouterKey.isNullOrBlank()) "Key saved"
                else "No key yet — add one below",
                openRouterEngine.available(),
            ),
            LegStatus(
                AiLeg.CLASSIC,
                "Smart Reply",
                "Always available",
                true,
            ),
            LegStatus(
                AiLeg.PLAIN,
                "Plain list",
                "Always available",
                true,
            ),
        )
    }
}
