package com.ppp65isking.box.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Small persistent store for Reddit app-only OAuth.
 *
 * Holds only the cached access token from the client-credentials grant.
 * The "installed app" client ID ships in the app in [OAuthConfig] —
 * nobody pastes it anywhere. Installed apps have no secret — the client
 * ID alone is the credential, and it is safe to ship on-device. No
 * Reddit account data lives here.
 */
class RedditAuthStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("box_reddit_auth", Context.MODE_PRIVATE)

    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS_TOKEN, null)
        set(value) {
            prefs.edit().putString(KEY_ACCESS_TOKEN, value).apply()
        }

    var tokenExpiresAt: Long
        get() = prefs.getLong(KEY_TOKEN_EXPIRES_AT, 0L)
        set(value) {
            prefs.edit().putLong(KEY_TOKEN_EXPIRES_AT, value).apply()
        }

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_TOKEN_EXPIRES_AT = "token_expires_at"
    }
}
