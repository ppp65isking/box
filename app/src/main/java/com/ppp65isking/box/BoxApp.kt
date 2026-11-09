package com.ppp65isking.box

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.ppp65isking.box.data.AccessTokenResult
import com.ppp65isking.box.data.DiscordClient
import com.ppp65isking.box.data.GoogleClient
import com.ppp65isking.box.data.GitHubClient
import com.ppp65isking.box.data.HealthConnectHelper
import com.ppp65isking.box.data.LoopbackOAuthServer
import com.ppp65isking.box.data.MicrosoftGraphClient
import com.ppp65isking.box.data.OAuthService
import com.ppp65isking.box.data.OAuthStore
import com.ppp65isking.box.data.RedditAuthStore
import com.ppp65isking.box.data.RedditClient
import com.ppp65isking.box.data.SmsRepository
import com.ppp65isking.box.data.SpotifyClient
import com.ppp65isking.box.data.SteamClient
import com.ppp65isking.box.data.SteamStore
import com.ppp65isking.box.data.UiPrefs
import com.ppp65isking.box.data.WeatherClient
import com.ppp65isking.box.data.ai.AiRouter
import com.ppp65isking.box.data.ai.LiteRtModelManager
import com.ppp65isking.box.data.buildAuthUrl
import com.ppp65isking.box.data.ensureAccessToken
import com.ppp65isking.box.data.exchangeCode
import com.ppp65isking.box.innertube.AuthStore
import com.ppp65isking.box.innertube.InnerTubeClient
import com.ppp65isking.box.innertube.StreamResolver
import com.ppp65isking.box.playback.PlayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Holds the app's singletons. The fragile parts are isolated by design:
 * everything YouTube-shaped lives in `innertube/` (one module to touch
 * when the API changes), everything playback-shaped in `playback/`,
 * everything OAuth-shaped in `data/OAuth*.kt` + the data clients.
 */
class BoxApp : Application() {

    private val okHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val authStore: AuthStore by lazy { AuthStore(this) }

    val innerTube: InnerTubeClient by lazy { InnerTubeClient(okHttp, authStore) }

    val streamResolver: StreamResolver by lazy { StreamResolver(innerTube) }

    val playerManager: PlayerManager by lazy { PlayerManager(this) }

    val gitHubClient: GitHubClient by lazy { GitHubClient(okHttp) }

    val redditAuthStore: RedditAuthStore by lazy { RedditAuthStore(this) }

    val redditClient: RedditClient by lazy { RedditClient(okHttp, redditAuthStore) }

    val weatherClient: WeatherClient by lazy { WeatherClient(okHttp) }

    val smsRepository: SmsRepository by lazy { SmsRepository(this) }

    val healthConnectHelper: HealthConnectHelper by lazy { HealthConnectHelper(this) }

    /** OAuth sign-ins (Google / Spotify / Discord) — one store for the
     *  signed-in users' tokens. The app's client IDs ship in the app
     *  in OAuthConfig.kt, not in this store. */
    val oauthStore: OAuthStore by lazy { OAuthStore(this) }

    /** Box's own UI preferences (floating bottom bar, …). */
    val uiPrefs: UiPrefs by lazy { UiPrefs(this) }

    val googleClient: GoogleClient by lazy { GoogleClient(okHttp) }

    val microsoftClient: MicrosoftGraphClient by lazy { MicrosoftGraphClient(okHttp) }

    val spotifyClient: SpotifyClient by lazy { SpotifyClient(okHttp) }

    val discordClient: DiscordClient by lazy { DiscordClient(okHttp) }

    val steamStore: SteamStore by lazy { SteamStore(this) }

    val steamClient: SteamClient by lazy { SteamClient(okHttp) }

    /** The downloaded on-device AI model (Qwen 1.5B .litertlm). */
    val liteRtModelManager: LiteRtModelManager by lazy {
        LiteRtModelManager(this, okHttp)
    }

    /** The AI router — one entry point for every AI feature.
     *  No-signup-only chain: Nano → Qwen → Pollinations → Smart Reply
     *  → plain list. No keys anywhere (see AiRouter). */
    val aiRouter: AiRouter by lazy {
        AiRouter(this, okHttp, liteRtModelManager)
    }

    private val oauthScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _oauthVersion = MutableStateFlow(0L)

    /** Bumped on every sign-in, sign-out, and failed login, so screens
     *  watching auth state refresh. Screens collect this, not the store. */
    val oauthVersion: StateFlow<Long> = _oauthVersion.asStateFlow()

    fun bumpOAuthVersion() {
        _oauthVersion.value = System.currentTimeMillis()
    }

    /** The last sign-in failure, in plain words, or null. Shown on the
     *  service's screen so a failed sign-in never fails silently —
     *  it carries the provider's own reason when there is one. */
    private val _lastOAuthError = MutableStateFlow<String?>(null)
    val lastOAuthError: StateFlow<String?> = _lastOAuthError.asStateFlow()

    fun clearOAuthError() {
        _lastOAuthError.value = null
    }

    fun signOut(service: OAuthService) {
        oauthStore.clearTokens(service)
        clearOAuthError()
        bumpOAuthVersion()
    }

    /**
     * A usable access token for the service, refreshing it when it
     * expired. Null when the user has to sign in again — in that case
     * the dead grant is cleared, so screens fall back to "Connect"
     * instead of showing a stale signed-in state. A network failure
     * while refreshing does NOT clear the grant: the next load retries
     * instead of signing the user out over a blip. Safe from any thread.
     */
    suspend fun accessToken(service: OAuthService): String? =
        withContext(Dispatchers.IO) {
            when (val result = ensureAccessToken(service, oauthStore, okHttp)) {
                is AccessTokenResult.Ok -> result.token
                AccessTokenResult.NeedsSignIn -> {
                    if (oauthStore.accessToken(service) != null) {
                        oauthStore.clearTokens(service)
                        bumpOAuthVersion()
                    }
                    null
                }
                AccessTokenResult.NetworkFailed -> null
            }
        }

    /**
     * Handle the box://oauth/<service>?code=…&state=… redirect that
     * comes back after a user signs in. Safe to call from onCreate and
     * onNewIntent. Validates the state (a mismatch means this redirect
     * did not come from a login Box started), exchanges the code for
     * tokens, fetches the account label, and bumps [oauthVersion].
     * Runs entirely on [oauthScope] — never on the main thread.
     */
    fun handleOAuthRedirect(uri: Uri) {
        if (uri.scheme != "box" || uri.host != "oauth") return
        val service = OAuthService.fromKey(uri.pathSegments.firstOrNull())
            ?: return
        val code = uri.getQueryParameter("code")
        val state = uri.getQueryParameter("state")
        val error = uri.getQueryParameter("error")
        clearOAuthError()
        oauthScope.launch {
            finishLogin(service, code, state, error)
        }
    }

    private var loopbackServer: LoopbackOAuthServer? = null

    /**
     * Google sign-in via a loopback redirect (see [LoopbackOAuthServer]):
     * Desktop OAuth clients cannot register a redirect URI, so Google
     * sends the browser to http://127.0.0.1:<port>/ and Box catches
     * the code there. Binds the server, opens the sign-in page in a
     * Custom Tab, and returns true. The tokens land through the same
     * [finishLogin] as the box:// flow. Call from a coroutine.
     */
    suspend fun startGoogleLogin(context: Context): Boolean {
        if (oauthStore.clientId(OAuthService.GOOGLE) == null) return false
        clearOAuthError()
        loopbackServer?.stop()
        val server = LoopbackOAuthServer { code, state, error ->
            oauthScope.launch {
                finishLogin(OAuthService.GOOGLE, code, state, error)
            }
        }
        loopbackServer = server
        val redirect = withContext(Dispatchers.IO) { server.bind() }
        oauthStore.pendingRedirectUri = redirect
        val url = buildAuthUrl(OAuthService.GOOGLE, oauthStore) ?: return false
        oauthScope.launch(Dispatchers.IO) {
            if (!server.serve() && loopbackServer === server) {
                // Nobody finished the login (closed tab / timed out) —
                // and this server is still the current one, so clearing
                // the pending login cannot wipe a newer retry's state.
                oauthStore.clearPendingLogin()
                _lastOAuthError.value = "The sign-in timed out — " +
                    "tap Connect and try again."
                bumpOAuthVersion()
            }
            if (loopbackServer === server) loopbackServer = null
        }
        val tabsIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
        tabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        tabsIntent.launchUrl(context, Uri.parse(url))
        return true
    }

    /** Shared tail of every sign-in: validate state, exchange the code
     *  for tokens, fetch the account label, bump [oauthVersion].
     *  Failures set [lastOAuthError] with the plain reason instead of
     *  failing silently. Runs on [oauthScope] — never on the main
     *  thread. */
    private suspend fun finishLogin(
        service: OAuthService,
        code: String?,
        state: String?,
        error: String?,
    ) {
        if (state != oauthStore.oauthState) {
            // Stale result — e.g. an older login's tab finishing after
            // a retry started a newer login. Leave the newer login's
            // pending state alone; only say it didn't match.
            _lastOAuthError.value = "The sign-in reply didn't match " +
                "Box's request — tap Connect and try again."
            bumpOAuthVersion()
            return
        }
        if (error != null || code == null) {
            // The user denied the sign-in, or the provider errored.
            oauthStore.clearPendingLogin()
            _lastOAuthError.value = if (error != null) {
                "${service.label} said the sign-in failed ($error)."
            } else {
                "The sign-in was cancelled."
            }
            bumpOAuthVersion()
            return
        }
        val exchange = exchangeCode(service, code, oauthStore, okHttp)
        oauthStore.clearPendingLogin()
        val tokens = exchange.tokens
        if (tokens == null) {
            _lastOAuthError.value = "${service.label} couldn't exchange " +
                "the sign-in code." +
                (exchange.error?.let { " ($it)" } ?: "")
            bumpOAuthVersion()
            return
        }
        _lastOAuthError.value = null
        oauthStore.setTokens(
            service = service,
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            expiresInSeconds = tokens.expiresInSeconds,
        )
        // Account label ("Signed in as …") from the profile endpoint.
        val label = when (service) {
            OAuthService.GOOGLE -> when (val r = googleClient.profile(tokens.accessToken)) {
                is GoogleClient.Result.Ok -> r.value.email ?: r.value.name
                else -> null
            }
            OAuthService.SPOTIFY -> when (val r = spotifyClient.me(tokens.accessToken)) {
                is SpotifyClient.Result.Ok -> r.value.email ?: r.value.displayName
                else -> null
            }
            OAuthService.DISCORD -> when (val r = discordClient.me(tokens.accessToken)) {
                is DiscordClient.Result.Ok -> r.value.email ?: r.value.username
                else -> null
            }
            OAuthService.MICROSOFT -> when (val r = microsoftClient.me(tokens.accessToken)) {
                is MicrosoftGraphClient.Result.Ok -> r.value
                else -> null
            }
        }
        if (label != null) oauthStore.setAccountLabel(service, label)
        bumpOAuthVersion()
    }
}
