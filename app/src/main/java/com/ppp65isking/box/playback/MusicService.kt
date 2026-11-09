package com.ppp65isking.box.playback

import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.ppp65isking.box.BoxApp

/**
 * MediaSessionService so audio keeps playing when the user leaves the app.
 *
 * The Application owns the ExoPlayer lifecycle — this service only owns
 * the MediaSession and must NOT release the player.
 */
class MusicService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = (application as BoxApp).playerManager.player
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}
