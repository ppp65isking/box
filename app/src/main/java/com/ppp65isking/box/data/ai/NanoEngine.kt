package com.ppp65isking.box.data.ai

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Gemini Nano via the ML Kit GenAI Prompt API (AICore). Free, instant,
 * private — but only on flagship phones (Pixel 8 Pro/9/10, S24/S25/S26,
 * OnePlus 13/15, …) and FOREGROUND-ONLY: inference while Box is in the
 * background returns BACKGROUND_USE_BLOCKED. Everything in Box that
 * uses Nano runs while Box is open, on tap — never in the background.
 *
 * A sideloaded app calling Nano is unproven until [statusText] runs on
 * a real phone: these are Play Services client libraries, so it should
 * work, but the Settings screen's "Check Nano" button is the test.
 */
class NanoEngine : AiEngine {
    override val leg = AiLeg.NANO
    override val label = "Nano"

    private val model by lazy { Generation.getClient() }

    override suspend fun available(): Boolean = runCatching {
        withContext(Dispatchers.IO) { model.checkStatus() } == FeatureStatus.AVAILABLE
    }.getOrDefault(false)

    /** Plain-words status for the Settings screen. */
    suspend fun statusText(): String = runCatching {
        when (withContext(Dispatchers.IO) { model.checkStatus() }) {
            FeatureStatus.AVAILABLE -> "Available on this phone"
            FeatureStatus.DOWNLOADABLE -> "Ready to download on this phone"
            FeatureStatus.DOWNLOADING -> "Downloading on this phone…"
            FeatureStatus.UNAVAILABLE ->
                "Not available on this phone (needs a newer Pixel / " +
                    "Galaxy / OnePlus, and a locked bootloader)"
            else -> "Unknown status"
        }
    }.getOrElse { "Couldn't check Nano (${it.message})" }

    /** Start the Nano download (only meaningful when the status is
     *  DOWNLOADABLE). Emits progress; the caller collects it. */
    fun download(): Flow<DownloadStatus> = model.download()

    override suspend fun run(task: AiTask): String? {
        val prompt = task.toPrompt() ?: return null
        if (!available()) return null
        return try {
            val response = withContext(Dispatchers.IO) {
                model.generateContent(prompt)
            }
            response.candidates.firstOrNull()?.text?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            // Includes BACKGROUND_USE_BLOCKED when Box isn't in the
            // foreground — the router just moves to the next leg.
            null
        }
    }
}
