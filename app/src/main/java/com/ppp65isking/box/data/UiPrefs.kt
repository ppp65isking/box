package com.ppp65isking.box.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Small persistent store for Box's own UI preferences.
 *
 * Exposes each setting as a StateFlow so the UI updates the moment a
 * toggle changes — the switch in More → Setup writes here and MainActivity
 * reads the same flow, no restart needed.
 */
class UiPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("box_ui_prefs", Context.MODE_PRIVATE)

    private val _floatingTextBar = MutableStateFlow(
        prefs.getBoolean(KEY_FLOATING_TEXT_BAR, false),
    )

    /** When true the bottom bar is a floating, text-only pill; when false
     *  it is the standard icon NavigationBar. Default off. */
    val floatingTextBar: StateFlow<Boolean> = _floatingTextBar.asStateFlow()

    fun setFloatingTextBar(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FLOATING_TEXT_BAR, enabled).apply()
        _floatingTextBar.value = enabled
    }

    companion object {
        private const val KEY_FLOATING_TEXT_BAR = "floating_text_bar"
    }
}
