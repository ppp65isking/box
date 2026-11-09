package com.ppp65isking.box.data

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * The three first-party OAuth services Box connects to.
 *
 * All three use Authorization Code + PKCE, so no client secret is needed
 * or stored anywhere. The client ID for each is Box's own developer
 * client, registered once by Anthony (see SETUP.md) and entered in
 * More → Setup. A user who taps Connect signs in with THEIR OWN Google /
 * Spotify / Discord account — Box then stores that user's token, never
 * Anthony's and never the developer's.
 *
 * Discord honesty note: the `messages.read` scope is NOT requested. Per
 * Discord's own docs it exists for local RPC server access only — a user
 * OAuth2 token cannot read channel messages through the HTTP API. Box
 * asks for `identify`, `email` and `guilds` (profile + server list), and
 * attempts the channel-messages endpoint as a best effort; if Discord
 * answers 401/403 the Discord screen says so plainly instead of
 * pretending the messages are just empty.
 */
enum class OAuthService(
    val key: String,
    val label: String,
    val authUrl: String,
    val tokenUrl: String,
    val scopes: List<String>,
) {
    GOOGLE(
        key = "google",
        label = "Google",
        authUrl = "https://accounts.google.com/o/oauth2/v2/auth",
        tokenUrl = "https://oauth2.googleapis.com/token",
        scopes = listOf(
            "openid",
            "email",
            "profile",
            "https://www.googleapis.com/auth/gmail.readonly",
            "https://www.googleapis.com/auth/calendar.readonly",
            "https://www.googleapis.com/auth/drive.readonly",
            "https://www.googleapis.com/auth/documents.readonly",
            "https://www.googleapis.com/auth/presentations.readonly",
            "https://www.googleapis.com/auth/photoslibrary.readonly",
        ),
    ),
    SPOTIFY(
        key = "spotify",
        label = "Spotify",
        authUrl = "https://accounts.spotify.com/authorize",
        tokenUrl = "https://accounts.spotify.com/api/token",
        scopes = listOf(
            "user-read-private",
            "user-read-email",
            "playlist-read-private",
            "playlist-read-collaborative",
            "user-library-read",
            "user-read-currently-playing",
            "user-read-playback-state",
            "user-modify-playback-state",
        ),
    ),
    DISCORD(
        key = "discord",
        label = "Discord",
        authUrl = "https://discord.com/oauth2/authorize",
        tokenUrl = "https://discord.com/api/v10/oauth2/token",
        // identify + email: profile. guilds: server list. No messages.read —
        // it is RPC-only per Discord's docs (see the class doc above).
        scopes = listOf("identify", "email", "guilds"),
    );

    companion object {
        fun fromKey(key: String?): OAuthService? =
            entries.firstOrNull { it.key == key }
    }
}

/** Where the provider sends the user back after sign-in. This exact URI
 *  must be registered for the app's OAuth client on each service
 *  (see SETUP.md). The intent-filter for it lives in AndroidManifest.xml. */
fun redirectUri(service: OAuthService): String = "box://oauth/${service.key}"

/** PKCE helpers. The verifier is 43 URL-safe characters (32 random
 *  bytes); the challenge is base64url(SHA-256(verifier)) with no padding. */
object Pkce {
    fun newVerifier(): String = randomUrlSafe(32)

    fun newState(): String = randomUrlSafe(16)

    fun challenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    private fun randomUrlSafe(bytes: Int): String {
        val buffer = ByteArray(bytes)
        SecureRandom().nextBytes(buffer)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer)
    }
}

private val json = Json { ignoreUnknownKeys = true }

/** Build the provider sign-in URL and remember the PKCE verifier + state
 *  so the redirect can be checked when it comes back. Returns null when
 *  the app's client ID for this service has not been entered yet. */
fun buildAuthUrl(service: OAuthService, store: OAuthStore): String? {
    val clientId = store.clientId(service) ?: return null
    val verifier = Pkce.newVerifier()
    val state = Pkce.newState()
    store.pkceVerifier = verifier
    store.oauthState = state
    store.pendingService = service.key
    val params = linkedMapOf(
        "client_id" to clientId,
        "response_type" to "code",
        "redirect_uri" to redirectUri(service),
        "scope" to service.scopes.joinToString(" "),
        "state" to state,
        "code_challenge" to Pkce.challenge(verifier),
        "code_challenge_method" to "S256",
    )
    if (service == OAuthService.GOOGLE) {
        // Offline access = a refresh token, so Box stays signed in.
        // prompt=consent makes Google re-issue the refresh token on
        // re-sign-in instead of silently dropping it.
        params["access_type"] = "offline"
        params["prompt"] = "consent"
    }
    if (service == OAuthService.DISCORD) {
        params["prompt"] = "consent"
    }
    val query = params.entries.joinToString("&") { (key, value) ->
        "$key=${URLEncoder.encode(value, Charsets.UTF_8.name())}"
    }
    return "${service.authUrl}?$query"
}

/** Open the provider sign-in page in a Custom Tab. Returns false when
 *  the service is not set up yet (no client ID in the store). */
fun launchOAuth(context: Context, service: OAuthService, store: OAuthStore): Boolean {
    val url = buildAuthUrl(service, store) ?: return false
    val tabsIntent = CustomTabsIntent.Builder()
        .setShowTitle(true)
        .build()
    tabsIntent.intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    tabsIntent.launchUrl(context, Uri.parse(url))
    return true
}

data class TokenResponse(
    val accessToken: String,
    val refreshToken: String?,
    val expiresInSeconds: Long,
)

/** Exchange an authorization code for tokens (PKCE — no client secret).
 *  Returns null when the provider refused the code. Callers must run
 *  this on Dispatchers.IO. */
fun exchangeCode(
    service: OAuthService,
    code: String,
    store: OAuthStore,
    okHttp: OkHttpClient,
): TokenResponse? {
    val clientId = store.clientId(service) ?: return null
    val verifier = store.pkceVerifier ?: return null
    val body = FormBody.Builder()
        .add("grant_type", "authorization_code")
        .add("code", code)
        .add("redirect_uri", redirectUri(service))
        .add("client_id", clientId)
        .add("code_verifier", verifier)
        .build()
    val request = Request.Builder()
        .url(service.tokenUrl)
        .post(body)
        .build()
    return runCatching {
        okHttp.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val root = json.parseToJsonElement(
                response.body?.string().orEmpty(),
            ).jsonObject
            val access = root["access_token"]?.jsonPrimitive?.contentOrNull
                ?: return null
            TokenResponse(
                accessToken = access,
                refreshToken = root["refresh_token"]?.jsonPrimitive?.contentOrNull,
                expiresInSeconds = root["expires_in"]?.jsonPrimitive?.intOrNull
                    ?.toLong() ?: 3600L,
            )
        }
    }.getOrNull()
}

/** Trade a refresh token for a fresh access token. Returns null when the
 *  refresh token is gone or the provider refused it — the user must sign
 *  in again in that case. Callers must run this on Dispatchers.IO. */
fun refreshAccessToken(
    service: OAuthService,
    store: OAuthStore,
    okHttp: OkHttpClient,
): TokenResponse? {
    val clientId = store.clientId(service) ?: return null
    val refresh = store.refreshToken(service) ?: return null
    val body = FormBody.Builder()
        .add("grant_type", "refresh_token")
        .add("refresh_token", refresh)
        .add("client_id", clientId)
        .build()
    val request = Request.Builder()
        .url(service.tokenUrl)
        .post(body)
        .build()
    return runCatching {
        okHttp.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val root = json.parseToJsonElement(
                response.body?.string().orEmpty(),
            ).jsonObject
            val access = root["access_token"]?.jsonPrimitive?.contentOrNull
                ?: return null
            TokenResponse(
                accessToken = access,
                // Providers may or may not rotate the refresh token; keep
                // the old one when the response has no new one.
                refreshToken = root["refresh_token"]?.jsonPrimitive?.contentOrNull
                    ?: refresh,
                expiresInSeconds = root["expires_in"]?.jsonPrimitive?.intOrNull
                    ?.toLong() ?: 3600L,
            )
        }
    }.getOrNull()
}

/** A usable access token for the service: the stored one when it is still
 *  fresh, a refreshed one when it expired, or null when the user needs to
 *  sign in again. Callers must run this on Dispatchers.IO. */
fun ensureAccessToken(
    service: OAuthService,
    store: OAuthStore,
    okHttp: OkHttpClient,
): String? {
    val current = store.accessToken(service) ?: return null
    val leewayMs = 60_000L
    if (System.currentTimeMillis() + leewayMs < store.tokenExpiresAt(service)) {
        return current
    }
    val refreshed = refreshAccessToken(service, store, okHttp) ?: return null
    store.setTokens(
        service = service,
        accessToken = refreshed.accessToken,
        refreshToken = refreshed.refreshToken,
        expiresInSeconds = refreshed.expiresInSeconds,
    )
    return refreshed.accessToken
}
