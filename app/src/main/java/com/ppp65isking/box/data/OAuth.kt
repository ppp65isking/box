package com.ppp65isking.box.data

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.ppp65isking.box.BuildConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * The four first-party OAuth services Box connects to.
 *
 * All four use Authorization Code + PKCE. Spotify, Discord and
 * Microsoft accept PKCE-only, so no client secret is needed or
 * stored for them. Google is the exception: its Desktop OAuth clients
 * reject the token endpoint with `invalid_request: client_secret is
 * missing` unless the Desktop client's secret is sent ALONGSIDE the
 * PKCE verifier, on both the code exchange and the refresh — even
 * though Google's docs label the secret "optional". That secret is
 * the app's own developer credential, injected by CI from the
 * GOOGLE_CLIENT_SECRET repo secret into BuildConfig; it never lives
 * in this repo or in the app's source (see SETUP.md). The client ID
 * for each is Box's own developer client, registered once by Anthony
 * (see SETUP.md) and shipped in the app in [OAuthConfig]. A user who
 * taps Connect signs in with THEIR OWN Google / Spotify / Discord /
 * Microsoft account — Box then stores that user's token, never
 * Anthony's and never the developer's.
 *
 * Microsoft note: the app registration is a public client under
 * "Mobile and desktop applications" in Entra, with the redirect URI
 * box://oauth/microsoft (a custom scheme — Entra accepts
 * <scheme>://<path> for native apps). Scopes are delegated Microsoft
 * Graph permissions; no admin consent is needed for these. The
 * client ID is blank until Anthony creates the registration (see
 * SETUP.md §5) — until then the Microsoft hub says plainly that
 * sign-in isn't available yet, exactly like Reddit's paused state.
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
            // Send: replying / composing needs its own scope — the
            // old grant was read-only. Adding a scope means the user
            // has to tap Connect again once (prompt=consent below
            // re-asks for the full list); that is expected, not a bug.
            "https://www.googleapis.com/auth/gmail.send",
            // Modify: starring and marking read (messages.modify) —
            // gmail.send does not cover it. Same reconnect-once note
            // as above.
            "https://www.googleapis.com/auth/gmail.modify",
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
    ),
    MICROSOFT(
        key = "microsoft",
        label = "Microsoft",
        authUrl = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize",
        tokenUrl = "https://login.microsoftonline.com/common/oauth2/v2.0/token",
        // Delegated Microsoft Graph scopes. offline_access is what
        // gets a refresh token, so Box stays signed in. All of these
        // are user-consentable — no admin consent needed.
        scopes = listOf(
            "User.Read",
            "Mail.Read",
            "Mail.Send",
            "Calendars.Read",
            "Files.Read",
            "offline_access",
        ),
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

/** POST to a token endpoint, retrying transport failures. A DNS
 *  failure, timeout, or dropped connection means the request never
 *  reached the provider, so the code / refresh token was not consumed
 *  and retrying is safe. A provider refusal (non-200) is returned at
 *  once and never retried. Callers must run this on Dispatchers.IO —
 *  the backoff sleeps on that thread. */
private sealed interface TokenPost {
    /** The provider answered — caller reads the body and closes it. */
    data class Responded(val response: Response) : TokenPost
    /** No answer after 3 tries (DNS / timeout / dropped connection). */
    data class NetworkFailed(val error: String) : TokenPost
}

private fun postToken(request: Request, okHttp: OkHttpClient): TokenPost {
    var lastError = "network error"
    repeat(3) { attempt ->
        try {
            return TokenPost.Responded(okHttp.newCall(request).execute())
        } catch (e: IOException) {
            // Never reached the provider — safe to retry.
            lastError = e.message ?: "network error"
            if (attempt < 2) Thread.sleep(if (attempt == 0) 1_000L else 2_000L)
        } catch (e: Exception) {
            return TokenPost.NetworkFailed(e.message ?: "network error")
        }
    }
    return TokenPost.NetworkFailed(lastError)
}

/** Google's Desktop OAuth client secret, injected by CI from the
 *  GOOGLE_CLIENT_SECRET repo secret into BuildConfig — null on local
 *  builds without the env var (Google sign-in then fails with the
 *  provider's own error instead of crashing). Google is the only
 *  service that ever sends it: Spotify and Discord accept PKCE-only. */
private fun googleClientSecret(): String? =
    BuildConfig.GOOGLE_CLIENT_SECRET.takeIf { it.isNotBlank() }

/** Exchange an authorization code for tokens (PKCE + Google's Desktop
 *  client_secret, sent together — never one without the other for
 *  Google). The provider's refusal reason comes back in
 *  [ExchangeResult.error] instead of failing silently, so the sign-in
 *  screen can say what actually went wrong. Callers must run this on
 *  Dispatchers.IO. */
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
    val bodyBuilder = FormBody.Builder()
        .add("grant_type", "authorization_code")
        .add("code", code)
        .add("redirect_uri", redirectUri(service, store))
        .add("client_id", clientId)
        .add("code_verifier", verifier)
    if (service == OAuthService.GOOGLE) {
        // Google's Desktop clients demand the client_secret alongside
        // the PKCE verifier — without it the exchange is refused with
        // `invalid_request: client_secret is missing.`
        googleClientSecret()?.let { bodyBuilder.add("client_secret", it) }
    }
    val body = bodyBuilder.build()
    val request = Request.Builder()
        .url(service.tokenUrl)
        .post(body)
        .build()
    return when (val post = postToken(request, okHttp)) {
        is TokenPost.NetworkFailed -> ExchangeResult(
            null,
            "couldn't reach ${service.label} — check your connection " +
                "and try again (${post.error})",
        )
        is TokenPost.Responded -> post.response.use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                ExchangeResult(null, providerError(raw))
            } else {
                val root = runCatching {
                    json.parseToJsonElement(raw).jsonObject
                }.getOrNull()
                if (root == null) {
                    ExchangeResult(
                        null,
                        "the provider answered something unreadable",
                    )
                } else {
                    val access = root["access_token"]?.jsonPrimitive?.contentOrNull
                    if (access == null) {
                        ExchangeResult(
                            null,
                            "the provider answered without an access token",
                        )
                    } else {
                        ExchangeResult(
                            TokenResponse(
                                accessToken = access,
                                refreshToken = root["refresh_token"]
                                    ?.jsonPrimitive?.contentOrNull,
                                expiresInSeconds = root["expires_in"]
                                    ?.jsonPrimitive?.intOrNull?.toLong() ?: 3600L,
                            ),
                            null,
                        )
                    }
                }
            }
        }
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

/** The outcome of trading a refresh token for a fresh access token.
 *  Refused and NetworkFailed look the same to a caller that only wants
 *  a token — but they must NOT be treated the same: a refused refresh
 *  means sign in again, a network failure means the stored grant is
 *  still good and the app must keep it. */
sealed interface RefreshResult {
    data class Ok(val tokens: TokenResponse) : RefreshResult
    /** The provider refused the refresh token (revoked / expired). */
    data object Refused : RefreshResult
    /** The request never reached the provider (DNS / timeout). */
    data object NetworkFailed : RefreshResult
}

/** Trade a refresh token for a fresh access token. For Google the
 *  Desktop client_secret goes along too — miss it and the account
 *  breaks ~1h after setup, when the first refresh runs. Callers must
 *  run this on Dispatchers.IO. */
fun refreshAccessToken(
    service: OAuthService,
    store: OAuthStore,
    okHttp: OkHttpClient,
): RefreshResult {
    val clientId = store.clientId(service) ?: return RefreshResult.Refused
    val refresh = store.refreshToken(service) ?: return RefreshResult.Refused
    val bodyBuilder = FormBody.Builder()
        .add("grant_type", "refresh_token")
        .add("refresh_token", refresh)
        .add("client_id", clientId)
    if (service == OAuthService.GOOGLE) {
        googleClientSecret()?.let { bodyBuilder.add("client_secret", it) }
    }
    val body = bodyBuilder.build()
    val request = Request.Builder()
        .url(service.tokenUrl)
        .post(body)
        .build()
    return when (val post = postToken(request, okHttp)) {
        is TokenPost.NetworkFailed -> RefreshResult.NetworkFailed
        is TokenPost.Responded -> post.response.use { response ->
            if (!response.isSuccessful) {
                RefreshResult.Refused
            } else {
                val root = runCatching {
                    json.parseToJsonElement(
                        response.body?.string().orEmpty(),
                    ).jsonObject
                }.getOrNull() ?: return RefreshResult.Refused
                val access = root["access_token"]?.jsonPrimitive?.contentOrNull
                    ?: return RefreshResult.Refused
                RefreshResult.Ok(
                    TokenResponse(
                        accessToken = access,
                        // Providers may or may not rotate the refresh
                        // token; keep the old one when the response has
                        // no new one.
                        refreshToken = root["refresh_token"]?.jsonPrimitive?.contentOrNull
                            ?: refresh,
                        expiresInSeconds = root["expires_in"]?.jsonPrimitive?.intOrNull
                            ?.toLong() ?: 3600L,
                    ),
                )
            }
        }
    }
}

/** The outcome of getting a usable access token. NetworkFailed keeps
 *  the stored grant — the next load retries instead of signing the
 *  user out over a blip. */
sealed interface AccessTokenResult {
    data class Ok(val token: String) : AccessTokenResult
    /** No stored token, or the provider refused the refresh — sign in again. */
    data object NeedsSignIn : AccessTokenResult
    /** The refresh never reached the provider — stored grant kept. */
    data object NetworkFailed : AccessTokenResult
}

/** A usable access token for the service: the stored one when it is still
 *  fresh, a refreshed one when it expired, or NeedsSignIn when the user
 *  needs to sign in again. Callers must run this on Dispatchers.IO. */
fun ensureAccessToken(
    service: OAuthService,
    store: OAuthStore,
    okHttp: OkHttpClient,
): AccessTokenResult {
    val current = store.accessToken(service) ?: return AccessTokenResult.NeedsSignIn
    val leewayMs = 60_000L
    if (System.currentTimeMillis() + leewayMs < store.tokenExpiresAt(service)) {
        return AccessTokenResult.Ok(current)
    }
    return when (val refreshed = refreshAccessToken(service, store, okHttp)) {
        is RefreshResult.Ok -> {
            store.setTokens(
                service = service,
                accessToken = refreshed.tokens.accessToken,
                refreshToken = refreshed.tokens.refreshToken,
                expiresInSeconds = refreshed.tokens.expiresInSeconds,
            )
            AccessTokenResult.Ok(refreshed.tokens.accessToken)
        }
        RefreshResult.Refused -> AccessTokenResult.NeedsSignIn
        RefreshResult.NetworkFailed -> AccessTokenResult.NetworkFailed
    }
}
