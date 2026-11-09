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
 * client, registered once by Anthony (see SETUP.md) and shipped in the
 * app in [OAuthConfig]. A user who taps Connect signs in with THEIR OWN
 * Google / Spotify / Discord account — Box then stores that user's token,
 * never Anthony's and never the developer's.
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
 *  (see SETUP.md) — except Google, which uses a loopback redirect:
 *  Desktop OAuth clients have no place to register a redirect URI,
 *  so Google sends the browser to http://127.0.0.1:<port>/ and the
 *  [LoopbackOAuthServer] catches it. The port is fresh per login and
 *  lives in [OAuthStore.pendingRedirectUri], so the token exchange
 *  sends exactly the URI the sign-in started with. The intent-filter
 *  for the box:// form lives in AndroidManifest.xml. */
fun redirectUri(service: OAuthService, store: OAuthStore): String =
    if (service == OAuthService.GOOGLE) {
        store.pendingRedirectUri ?: "http://127.0.0.1/"
    } else {
        "box://oauth/${service.key}"
    }

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
 *  the app's client ID for this service has not been added to
 *  [OAuthConfig] yet. */
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
        "redirect_uri" to redirectUri(service, store),
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
 *  the service is not set up yet (no client ID in [OAuthConfig]). */
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

data class ExchangeResult(
    val tokens: TokenResponse?,
    /** Plain provider reason when [tokens] is null — e.g. invalid_grant
     *  or redirect_uri_mismatch. No codes or tokens, just the error. */
    val error: String?,
)

/** Exchange an authorization code for tokens (PKCE — no client secret).
 *  The provider's refusal reason comes back in [ExchangeResult.error]
 *  instead of failing silently, so the sign-in screen can say what
 *  actually went wrong. Callers must run this on Dispatchers.IO. */
fun exchangeCode(
    service: OAuthService,
    code: String,
    store: OAuthStore,
    okHttp: OkHttpClient,
): ExchangeResult {
    val clientId = store.clientId(service)
        ?: return ExchangeResult(null, "no client ID for this service")
    val verifier = store.pkceVerifier
        ?: return ExchangeResult(null, "the sign-in session was lost")
    val body = FormBody.Builder()
        .add("grant_type", "authorization_code")
        .add("code", code)
        .add("redirect_uri", redirectUri(service, store))
        .add("client_id", clientId)
        .add("code_verifier", verifier)
        .build()
    val request = Request.Builder()
        .url(service.tokenUrl)
        .post(body)
        .build()
    return runCatching {
        okHttp.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return ExchangeResult(null, providerError(raw))
            }
            val root = json.parseToJsonElement(raw).jsonObject
            val access = root["access_token"]?.jsonPrimitive?.contentOrNull
                ?: return ExchangeResult(
                    null,
                    "the provider answered without an access token",
                )
            ExchangeResult(
                TokenResponse(
                    accessToken = access,
                    refreshToken = root["refresh_token"]?.jsonPrimitive?.contentOrNull,
                    expiresInSeconds = root["expires_in"]?.jsonPrimitive?.intOrNull
                        ?.toLong() ?: 3600L,
                ),
                null,
            )
        }
    }.getOrElse { e ->
        ExchangeResult(null, e.message ?: "network error")
    }
}

/** Pull the short "error" / "error_description" out of a provider
 *  error body, e.g. invalid_grant. Falls back to the raw body,
 *  truncated — it never contains codes or tokens. */
private fun providerError(raw: String): String {
    val parsed = runCatching {
        json.parseToJsonElement(raw).jsonObject
    }.getOrNull()
    val code = parsed?.get("error")?.jsonPrimitive?.contentOrNull
    val detail = parsed?.get("error_description")?.jsonPrimitive?.contentOrNull
    val combined = listOfNotNull(code, detail).joinToString(": ")
    return combined.ifEmpty { raw.take(200) }.ifEmpty { "provider refused" }
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
