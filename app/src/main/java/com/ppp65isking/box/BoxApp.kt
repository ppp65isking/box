package com.ppp65isking.box

import android.app.Application
import android.net.Uri
import com.ppp65isking.box.data.DiscordClient
import com.ppp65isking.box.data.GoogleClient
import com.ppp65isking.box.data.GitHubClient
import com.ppp65isking.box.data.HealthConnectHelper
import com.ppp65isking.box.data.OAuthService
import com.ppp65isking.box.data.OAuthStore
import com.ppp65isking.box.data.RedditAuthStore
import com.ppp65isking.box.data.RedditClient
import com.ppp65isking.box.data.SmsRepository
import com.ppp65isking.box.data.SpotifyClient
import com.ppp65isking.box.data.UiPrefs
import com.ppp65isking.box.data.WeatherClient
import com.ppp65isking.box.data.ensureAccessToken
import com.ppp65isking.box.data.exchangeCode
import com.ppp65isking.box.data.googleRedirectScheme
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

    val spotifyClient: SpotifyClient by lazy { SpotifyClient(okHttp) }

    val discordClient: DiscordClient by lazy { DiscordClient(okHttp) }

    private val oauthScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _oauthVersion = MutableStateFlow(0L)

    /** Bumped on every sign-in, sign-out, and failed login, so screens
     *  watching auth state refresh. Screens collect this, not the store. */
    val oauthVersion: StateFlow<Long> = _oauthVersion.asStateFlow()

    fun bumpOAuthVersion() {
        _oauthVersion.value = System.currentTimeMillis()
    }

    fun signOut(service: OAuthService) {
        oauthStore.clearTokens(service)
        bumpOAuthVersion()
    }

    /**
     * A usable access token for the service, refreshing it when it
     * expired. Null when the user has to sign in again — in that case
     * the dead grant is cleared, so screens fall back to "Connect"
     * instead of showing a stale signed-in state. Safe from any thread.
     */
    suspend fun accessToken(service: OAuthService): String? =
        withContext(Dispatchers.IO) {
            val token = ensureAccessToken(service, oauthStore, okHttp)
            if (token == null && oauthStore.accessToken(service) != null) {
                oauthStore.clearTokens(service)
                bumpOAuthVersion()
            }
            token
        }

    /**
     * Handle the box://oauth/<service>?code=…&state=… redirect — or
     * Google's reverse-DNS redirect
     * (com.googleusercontent.apps.<number>:/oauth2redirect) — that
     * comes back after a user signs in. Safe to call from onCreate and
     * onNewIntent. Validates the state (a mismatch means this redirect
     * did not come from a login Box started), exchanges the code for
     * tokens, fetches the account label, and bumps [oauthVersion].
     * Runs entirely on [oauthScope] — never on the main thread.
     */
    fun handleOAuthRedirect(uri: Uri) {
        val service = when {
            uri.scheme == "box" && uri.host == "oauth" ->
                OAuthService.fromKey(uri.pathSegments.firstOrNull())
            uri.scheme == googleRedirectScheme() &&
                uri.path == "/oauth2redirect" -> OAuthService.GOOGLE
            else -> return
        } ?: return
        val code = uri.getQueryParameter("code")
        val state = uri.getQueryParameter("state")
        val error = uri.getQueryParameter("error")
        oauthScope.launch {
            if (error != null || code == null) {
                // The user denied the sign-in, or the provider errored.
                oauthStore.clearPendingLogin()
                bumpOAuthVersion()
                return@launch
            }
            if (state == null || state != oauthStore.oauthState) {
                oauthStore.clearPendingLogin()
                bumpOAuthVersion()
                return@launch
            }
            val tokens = exchangeCode(service, code, oauthStore, okHttp)
            oauthStore.clearPendingLogin()
            if (tokens == null) {
                bumpOAuthVersion()
                return@launch
            }
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
            }
            if (label != null) oauthStore.setAccountLabel(service, label)
            bumpOAuthVersion()
        }
    }
}
