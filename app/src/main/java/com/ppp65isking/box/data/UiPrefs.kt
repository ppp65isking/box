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

    private val _railOnLeft = MutableStateFlow(
        prefs.getBoolean(KEY_RAIL_ON_LEFT, false),
    )

    /** On foldables the nav is a vertical icon rail at the bottom
     *  right; when true it sits at the bottom left instead. Phones
     *  ignore this. Default right. */
    val railOnLeft: StateFlow<Boolean> = _railOnLeft.asStateFlow()

    fun setRailOnLeft(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_RAIL_ON_LEFT, enabled).apply()
        _railOnLeft.value = enabled
    }

    private val _previewRail = MutableStateFlow(
        prefs.getBoolean(KEY_PREVIEW_RAIL, false),
    )

    /** Show the foldable icon rail on any phone, to preview the
     *  foldable layout without a foldable. Default off. */
    val previewRail: StateFlow<Boolean> = _previewRail.asStateFlow()

    fun setPreviewRail(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PREVIEW_RAIL, enabled).apply()
        _previewRail.value = enabled
    }

    private val _mostUsedFirst = MutableStateFlow(
        prefs.getBoolean(KEY_MOST_USED_FIRST, false),
    )

    /** When true (and usage access is granted), the Home grid orders
     *  apps by how recently they were used, and "Jump back in" fills
     *  from last-used data. Default off — the grid stays in catalog
     *  order until the user opts in. */
    val mostUsedFirst: StateFlow<Boolean> = _mostUsedFirst.asStateFlow()

    fun setMostUsedFirst(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MOST_USED_FIRST, enabled).apply()
        _mostUsedFirst.value = enabled
    }

    companion object {
        private const val KEY_FLOATING_BAR = "floating_bar"
        private const val KEY_HEALTH_CONNECT = "health_connect"
        private const val KEY_RAIL_ON_LEFT = "rail_on_left"
        private const val KEY_PREVIEW_RAIL = "preview_rail"
        private const val KEY_MOST_USED_FIRST = "most_used_first"
    }
}
