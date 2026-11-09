package com.ppp65isking.box.data.ai

import android.content.Context

/**
 * The user's own free cloud keys — the same pattern as the Steam key
 * (SteamStore): typed in by the user on the AI settings screen, stored
 * only on this device, sent only to that provider. Nothing is embedded
 * in the app or the repo; no key = that cloud leg simply isn't in the
 * chain. No key here can bill anyone: none of the three free tiers
 * takes a card, and a spent quota answers 429 (OpenRouter: 402).
 */
class AiKeyStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("box_ai_keys", Context.MODE_PRIVATE)

    var groqKey: String?
        get() = prefs.getString(KEY_GROQ, null)?.takeIf { it.isNotBlank() }
        set(value) {
            prefs.edit().putString(KEY_GROQ, value?.trim().orEmpty()).apply()
        }

    var geminiKey: String?
        get() = prefs.getString(KEY_GEMINI, null)?.takeIf { it.isNotBlank() }
        set(value) {
            prefs.edit().putString(KEY_GEMINI, value?.trim().orEmpty()).apply()
        }

    var openRouterKey: String?
        get() = prefs.getString(KEY_OPENROUTER, null)?.takeIf { it.isNotBlank() }
        set(value) {
            prefs.edit().putString(KEY_OPENROUTER, value?.trim().orEmpty()).apply()
        }

    companion object {
        private const val KEY_GROQ = "groq_key"
        private const val KEY_GEMINI = "gemini_key"
        private const val KEY_OPENROUTER = "openrouter_key"
    }
}
