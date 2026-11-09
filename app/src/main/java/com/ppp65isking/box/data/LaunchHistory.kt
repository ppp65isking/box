package com.ppp65isking.box.data

import android.content.Context

/**
 * "Jump back in" — the last apps launched from Box, newest first.
 * Package names only (no PII); capped at 8.
 */
class LaunchHistory(context: Context) {

    private val prefs =
        context.getSharedPreferences("box_launch_history", Context.MODE_PRIVATE)

    fun record(packageName: String) {
        val current = recent().filterNot { it == packageName }
        val updated = (listOf(packageName) + current).take(MAX_KEPT)
        prefs.edit().putString(KEY, updated.joinToString(SEPARATOR)).apply()
    }

    fun recent(): List<String> =
        prefs.getString(KEY, null)
            ?.split(SEPARATOR)
            ?.filter { it.isNotBlank() }
            .orEmpty()

    companion object {
        private const val KEY = "recent_packages"
        private const val SEPARATOR = ","
        private const val MAX_KEPT = 8
    }
}
