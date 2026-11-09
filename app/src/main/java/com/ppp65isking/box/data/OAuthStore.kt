package com.ppp65isking.box.data

import android.content.Context
import android.content.SharedPreferences

/**
 * One persistent store for all OAuth sign-ins.
 *
 * Holds, per service, the signed-in USER's access token, refresh
 * token, expiry, and a short account label ("Signed in as …"), plus
 * the in-flight PKCE verifier + state for the login that is open in
 * the browser right now.
 *
 * The app's own client IDs are NOT stored here — they ship in the app
 * in [OAuthConfig] (see SETUP.md). End users never see or enter them;
 * they just tap Connect and sign in with their own account.
 *
 * Tokens are stored in private SharedPreferences and are never logged.
 * No client secrets live here — all three services use PKCE, so a public
 * client ID is the whole credential.
 */
class OAuthStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("box_oauth", Context.MODE_PRIVATE)

    /** The app's client ID for this service, from [OAuthConfig]. */
    fun clientId(service: OAuthService): String? =
        service.configuredClientId()

    fun accessToken(service: OAuthService): String? =
        prefs.getString("${service.key}_access_token", null)

    fun refreshToken(service: OAuthService): String? =
        prefs.getString("${service.key}_refresh_token", null)

    fun tokenExpiresAt(service: OAuthService): Long =
        prefs.getLong("${service.key}_expires_at", 0L)

    fun accountLabel(service: OAuthService): String? =
        prefs.getString("${service.key}_account", null)

    fun setTokens(
        service: OAuthService,
        accessToken: String,
        refreshToken: String?,
        expiresInSeconds: Long,
        accountLabel: String? = null,
    ) {
        prefs.edit()
            .putString("${service.key}_access_token", accessToken)
            .putString("${service.key}_refresh_token", refreshToken)
            .putLong(
                "${service.key}_expires_at",
                System.currentTimeMillis() + expiresInSeconds * 1_000L,
            )
            .apply()
        if (accountLabel != null) {
            prefs.edit().putString("${service.key}_account", accountLabel).apply()
        }
    }

    fun setAccountLabel(service: OAuthService, label: String?) {
        prefs.edit().putString("${service.key}_account", label).apply()
    }

    fun clearTokens(service: OAuthService) {
        prefs.edit()
            .remove("${service.key}_access_token")
            .remove("${service.key}_refresh_token")
            .remove("${service.key}_expires_at")
            .remove("${service.key}_account")
            .apply()
    }

    fun isConfigured(service: OAuthService): Boolean =
        clientId(service) != null

    fun isSignedIn(service: OAuthService): Boolean =
        accessToken(service) != null

    var pkceVerifier: String?
        get() = prefs.getString(KEY_PKCE_VERIFIER, null)
        set(value) {
            prefs.edit().putString(KEY_PKCE_VERIFIER, value).apply()
        }

    var oauthState: String?
        get() = prefs.getString(KEY_OAUTH_STATE, null)
        set(value) {
            prefs.edit().putString(KEY_OAUTH_STATE, value).apply()
        }

    /** Which service the in-flight login belongs to ("google"/"spotify"/"discord"). */
    var pendingService: String?
        get() = prefs.getString(KEY_PENDING_SERVICE, null)
        set(value) {
            prefs.edit().putString(KEY_PENDING_SERVICE, value).apply()
        }

    fun clearPendingLogin() {
        prefs.edit()
            .remove(KEY_PKCE_VERIFIER)
            .remove(KEY_OAUTH_STATE)
            .remove(KEY_PENDING_SERVICE)
            .apply()
    }

    companion object {
        private const val KEY_PKCE_VERIFIER = "pkce_verifier"
        private const val KEY_OAUTH_STATE = "oauth_state"
        private const val KEY_PENDING_SERVICE = "pending_service"
    }
}
