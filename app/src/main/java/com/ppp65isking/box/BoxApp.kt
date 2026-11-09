package com.ppp65isking.box

import android.app.Application
import com.ppp65isking.box.data.GitHubClient
import com.ppp65isking.box.data.HealthConnectHelper
import com.ppp65isking.box.data.RedditAuthStore
import com.ppp65isking.box.data.RedditClient
import com.ppp65isking.box.data.SmsRepository
import com.ppp65isking.box.data.WeatherClient
import com.ppp65isking.box.innertube.AuthStore
import com.ppp65isking.box.innertube.InnerTubeClient
import com.ppp65isking.box.innertube.StreamResolver
import com.ppp65isking.box.playback.PlayerManager
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Holds the app's singletons. The fragile parts are isolated by design:
 * everything YouTube-shaped lives in `innertube/` (one module to touch
 * when the API changes), everything playback-shaped in `playback/`.
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
}
