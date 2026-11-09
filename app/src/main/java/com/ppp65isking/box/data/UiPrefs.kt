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
 * toggle changes — the switch in Settings writes here and MainActivity
 * reads the same flow, no restart needed.
 */
class UiPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("box_ui_prefs", Context.MODE_PRIVATE)

    private val _floatingBar = MutableStateFlow(
        prefs.getBoolean(KEY_FLOATING_BAR, true),
    )

    /** When true the bottom bar is a floating, icon-only pill that blurs
     *  the content scrolling under it; when false it is the standard
     *  icon NavigationBar. Default on. */
    val floatingBar: StateFlow<Boolean> = _floatingBar.asStateFlow()

    private val _healthConnect = MutableStateFlow(
        prefs.getBoolean(KEY_HEALTH_CONNECT, true),
    )

    /** When false, Home skips Health Connect entirely — no steps read,
     *  no steps card. Default on. */
    val healthConnect: StateFlow<Boolean> = _healthConnect.asStateFlow()

    fun setFloatingBar(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FLOATING_BAR, enabled).apply()
        _floatingBar.value = enabled
    }

    fun setHealthConnect(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HEALTH_CONNECT, enabled).apply()
        _healthConnect.value = enabled
    }

    companion object {
        private const val KEY_FLOATING_BAR = "floating_bar"
        private const val KEY_HEALTH_CONNECT = "health_connect"
    }
}
