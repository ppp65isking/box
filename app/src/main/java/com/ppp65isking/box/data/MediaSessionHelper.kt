package com.ppp65isking.box.data

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import com.ppp65isking.box.service.BoxNotificationListener

/** Now-playing info from another app (Spotify, YT Music, …) as seen
 *  through the media session. */
data class ExternalNowPlaying(
    val packageName: String,
    val appName: String,
    val title: String?,
    val artist: String?,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
)

/**
 * MediaSession now-playing from other apps — visible to an enabled
 * notification listener, so no new permission is needed. Box's own
 * in-app player is filtered out (its own package); only other apps
 * show up here.
 *
 * Empty when the listener isn't granted yet, nothing is playing, or
 * the system refuses the query — callers treat that as "nothing
 * playing elsewhere", never an error. Callers refresh on a short
 * poll while the Music tab is open, so the position stays live.
 */
object MediaSessionHelper {

    fun activeSessions(context: Context): List<ExternalNowPlaying> {
        val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE)
            as? MediaSessionManager ?: return emptyList()
        val listenerComponent = ComponentName(
            context,
            BoxNotificationListener::class.java,
        )
        return runCatching {
            manager.getActiveSessions(listenerComponent)
                .filter { it.packageName != context.packageName }
                .mapNotNull { controller -> toNowPlaying(context, controller) }
        }.getOrDefault(emptyList())
    }

    private fun toNowPlaying(
        context: Context,
        controller: MediaController,
    ): ExternalNowPlaying? {
        val metadata = controller.metadata ?: return null
        val state = controller.playbackState
        val title = metadata.getString(
            android.media.MediaMetadata.METADATA_KEY_TITLE,
        )
        val artist = metadata.getString(
            android.media.MediaMetadata.METADATA_KEY_ARTIST,
        )
        // A session with no title at all is a placeholder, not a song.
        if (title.isNullOrBlank() && artist.isNullOrBlank()) return null
        val duration = metadata.getLong(
            android.media.MediaMetadata.METADATA_KEY_DURATION,
        )
        return ExternalNowPlaying(
            packageName = controller.packageName,
            appName = appLabel(context, controller.packageName),
            title = title,
            artist = artist,
            isPlaying = state?.state == PlaybackState.STATE_PLAYING,
            positionMs = state?.position?.coerceAtLeast(0L) ?: 0L,
            durationMs = duration.coerceAtLeast(0L),
        )
    }

    private fun appLabel(context: Context, packageName: String): String =
        runCatching {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        }.getOrElse { packageName }
}
