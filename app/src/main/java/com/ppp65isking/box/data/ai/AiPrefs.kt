package com.ppp65isking.box.data.ai

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Box's own AI preferences: which leg the router prefers, and which
 *  engine answered last (shown in the UI as "AI: Nano"). */
class AiPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("box_ai_prefs", Context.MODE_PRIVATE)

    private val _mode = MutableStateFlow(mode)
    val modeFlow: StateFlow<AiMode> = _mode.asStateFlow()

    var mode: AiMode
        get() = runCatching {
            AiMode.valueOf(
                prefs.getString(KEY_MODE, AiMode.AUTO.name) ?: AiMode.AUTO.name,
            )
        }.getOrDefault(AiMode.AUTO)
        set(value) {
            prefs.edit().putString(KEY_MODE, value.name).apply()
            _mode.value = value
        }

    private val _lastEngine = MutableStateFlow(
        prefs.getString(KEY_LAST_ENGINE, null),
    )
    val lastEngine: StateFlow<String?> = _lastEngine.asStateFlow()

    fun setLastEngine(label: String) {
        prefs.edit().putString(KEY_LAST_ENGINE, label).apply()
        _lastEngine.value = label
    }

    companion object {
        private const val KEY_MODE = "ai_mode"
        private const val KEY_LAST_ENGINE = "ai_last_engine"
    }
}
