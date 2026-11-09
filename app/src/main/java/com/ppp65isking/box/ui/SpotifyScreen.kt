package com.ppp65isking.box.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.ppp65isking.box.ui.components.BoxHeader
import com.ppp65isking.box.ui.components.OAuthStatusCard
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ppp65isking.box.BoxApp
import com.ppp65isking.box.data.OAuthService
import com.ppp65isking.box.data.SpotifyClient
import com.ppp65isking.box.data.launchOAuth
import com.ppp65isking.box.model.SpotifyNowPlaying
import com.ppp65isking.box.model.SpotifyPlaylist
import com.ppp65isking.box.model.SpotifyProfile
import com.ppp65isking.box.model.SpotifyTrack
import kotlinx.coroutines.launch

private data class SpotifyData(
    val profile: SpotifyProfile?,
    val playlists: List<SpotifyPlaylist>,
    val tracks: List<SpotifyTrack>,
    val nowPlaying: SpotifyNowPlaying?,
)

private sealed interface SpotifyState {
    data object Idle : SpotifyState
    data object Loading : SpotifyState
    data class Ok(val data: SpotifyData) : SpotifyState
    data class Error(val message: String) : SpotifyState
}

/** Your Spotify library, playlists, and what's playing — plus play/pause
 *  on your own active player. Playback control needs Premium and an
 *  active Spotify device; anything else is shown as plain text. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotifyScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as BoxApp
    val scope = rememberCoroutineScope()

    val oauthVersion by app.oauthVersion.collectAsState()
    val configured = app.oauthStore.isConfigured(OAuthService.SPOTIFY)
    val signedIn = app.oauthStore.isSignedIn(OAuthService.SPOTIFY)
    val accountLabel = app.oauthStore.accountLabel(OAuthService.SPOTIFY)

    var state by remember { mutableStateOf<SpotifyState>(SpotifyState.Idle) }
    var playbackMessage by remember { mutableStateOf<String?>(null) }

    fun load() {
        state = SpotifyState.Loading
        scope.launch {
            val token = app.accessToken(OAuthService.SPOTIFY)
            if (token == null) {
                state = SpotifyState.Error(
                    "Your Spotify session ended — connect again.",
                )
                return@launch
            }
            val profile = when (val r = app.spotifyClient.me(token)) {
                is SpotifyClient.Result.Ok -> r.value
                is SpotifyClient.Result.Unauthorized -> {
                    state = SpotifyState.Error(
                        "Spotify said the sign-in is stale — connect again.",
                    )
                    return@launch
                }
                is SpotifyClient.Result.Error -> {
                    state = SpotifyState.Error("Couldn't load profile: ${r.message}")
                    return@launch
                }
                is SpotifyClient.Result.PlaybackUnavailable -> null
            }
            val playlists = when (val r = app.spotifyClient.playlists(token)) {
                is SpotifyClient.Result.Ok -> r.value
                else -> emptyList()
            }
            val tracks = when (val r = app.spotifyClient.savedTracks(token)) {
                is SpotifyClient.Result.Ok -> r.value
                else -> emptyList()
            }
            val nowPlaying = when (val r = app.spotifyClient.currentlyPlaying(token)) {
                is SpotifyClient.Result.Ok -> r.value
                else -> null
            }
            state = SpotifyState.Ok(
                SpotifyData(profile, playlists, tracks, nowPlaying),
            )
        }
    }

    fun togglePlayback(currentlyPlaying: Boolean) {
        playbackMessage = null
        scope.launch {
            val token = app.accessToken(OAuthService.SPOTIFY) ?: return@launch
            val result = if (currentlyPlaying) {
                app.spotifyClient.pause(token)
            } else {
                app.spotifyClient.play(token)
            }
            when (result) {
                is SpotifyClient.Result.Ok -> load()
                is SpotifyClient.Result.PlaybackUnavailable ->
                    playbackMessage = result.message
                is SpotifyClient.Result.Unauthorized ->
                    playbackMessage = "Your Spotify session ended — connect again."
                is SpotifyClient.Result.Error ->
                    playbackMessage = "Couldn't control playback: ${result.message}"
            }
        }
    }

    // Reload whenever the auth state changes — e.g. right after sign-in.
    LaunchedEffect(oauthVersion) {
        if (signedIn) load()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BoxHeader("Spotify")
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            OAuthStatusCard(
                service = OAuthService.SPOTIFY,
                configured = configured,
                signedIn = signedIn,
                accountLabel = accountLabel,
                onConnect = { launchOAuth(context, OAuthService.SPOTIFY, app.oauthStore) },
                onSignOut = { app.signOut(OAuthService.SPOTIFY) },
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (!signedIn) {
            Text(
                text = "Your playlists, liked songs, and what's playing live " +
                    "here once an account is connected.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            return@Column
        }
        when (val current = state) {
            is SpotifyState.Idle -> {
                Text(text = "Loading…", modifier = Modifier.padding(16.dp))
            }
            is SpotifyState.Loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                }
            }
            is SpotifyState.Error -> {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = current.message,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { load() }) { Text("Retry") }
                }
            }
            is SpotifyState.Ok -> {
                val data = current.data
                LazyColumn {
                    data.nowPlaying?.track?.let { track ->
                        item {
                            NowPlayingCard(
                                nowPlaying = data.nowPlaying,
                                onToggle = {
                                    togglePlayback(data.nowPlaying.isPlaying)
                                },
                            )
                        }
                    }
                    if (data.nowPlaying == null) {
                        item {
                            Text(
                                text = "Nothing playing right now.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                    playbackMessage?.let { message ->
                        item {
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                    }
                    if (data.playlists.isNotEmpty()) {
                        item {
                            SectionTitle("Your playlists")
                        }
                        items(data.playlists, key = { it.id }) { playlist ->
                            PlaylistRow(playlist = playlist)
                        }
                    }
                    if (data.tracks.isNotEmpty()) {
                        item {
                            SectionTitle("Liked songs")
                        }
                        items(data.tracks, key = { it.id }) { track ->
                            TrackRow(track = track)
                        }
                    }
                    if (data.playlists.isEmpty() && data.tracks.isEmpty()) {
                        item {
                            Text(
                                text = "No playlists or liked songs yet.",
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun NowPlayingCard(
    nowPlaying: SpotifyNowPlaying,
    onToggle: () -> Unit,
) {
    val track = nowPlaying.track ?: return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (track.imageUrl != null) {
            AsyncImage(
                model = track.imageUrl,
                contentDescription = track.albumName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape),
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artists.joinToString(", "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (nowPlaying.isPlaying) "Playing now" else "Paused",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onToggle) {
            Icon(
                imageVector = if (nowPlaying.isPlaying) {
                    Icons.Filled.Pause
                } else {
                    Icons.Filled.PlayArrow
                },
                contentDescription = if (nowPlaying.isPlaying) "Pause" else "Play",
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

@Composable
private fun PlaylistRow(playlist: SpotifyPlaylist) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (playlist.imageUrl != null) {
            AsyncImage(
                model = playlist.imageUrl,
                contentDescription = playlist.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${playlist.trackCount} songs" +
                    (playlist.owner?.let { " · by $it" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TrackRow(track: SpotifyTrack) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (track.imageUrl != null) {
            AsyncImage(
                model = track.imageUrl,
                contentDescription = track.albumName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artists.joinToString(", ") +
                    (track.albumName?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
