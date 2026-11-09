package com.ppp65isking.box.data

import android.content.Context

/**
 * The user's own Steam setup: their Steam Web API key and their
 * SteamID64, both typed in by the user on the Steam screen.
 *
 * A Steam API key is a per-user secret, not a shared client ID — it is
 * never embedded in the app and never leaves the device except in
 * requests to api.steampowered.com. Clearing it disconnects Steam.
 */
class SteamStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("steam", Context.MODE_PRIVATE)

    /** Both a key and a SteamID are saved. */
    val isConfigured: Boolean
        get() = !apiKey.isNullOrBlank() && !steamId.isNullOrBlank()

    var apiKey: String?
        get() = prefs.getString(KEY_API_KEY, null)
        private set(value) {
            prefs.edit().putString(KEY_API_KEY, value).apply()
        }

    var steamId: String?
        get() = prefs.getString(KEY_STEAM_ID, null)
        private set(value) {
            prefs.edit().putString(KEY_STEAM_ID, value).apply()
        }

    fun save(key: String, steamId: String) {
        apiKey = key.trim()
        this.steamId = steamId.trim()
    }

    fun clear() {
        prefs.edit().remove(KEY_API_KEY).remove(KEY_STEAM_ID).apply()
    }

    companion object {
        private const val KEY_API_KEY = "api_key"
        private const val KEY_STEAM_ID = "steam_id"
    }
}
