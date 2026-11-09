package com.ppp65isking.box.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.ppp65isking.box.data.openInYtMusic
import com.ppp65isking.box.innertube.InnerTubeClient
import com.ppp65isking.box.innertube.Renderers
import com.ppp65isking.box.innertube.Song
import com.ppp65isking.box.ui.components.NowPlayingBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface MusicSearchState {
    data object Idle : MusicSearchState
    data object Loading : MusicSearchState
    data class Ok(val tracks: List<Song>) : MusicSearchState
    data class Error(val message: String) : MusicSearchState
}

/** YT Music search + playback via InnerTube (no sign-in needed).
 *  Stream URLs expire in ~6h, so every play resolves fresh. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as BoxApp
    val scope = rememberCoroutineScope()
    val playerState by app.playerManager.state.collectAsState()

    var query by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<MusicSearchState>(MusicSearchState.Idle) }
    var playingId by remember { mutableStateOf<String?>(null) }
    var playError by remember { mutableStateOf<String?>(null) }

    fun search() {
        if (query.isBlank()) return
        state = MusicSearchState.Loading
        scope.launch {
            state = withContext(Dispatchers.IO) {
                runCatching {
                    val response = app.innerTube.search(
                        query,
                        InnerTubeClient.SearchParams.SONGS,
                    )
                    Renderers.parseSearchSongs(response)
                }.fold(
                    onSuccess = { MusicSearchState.Ok(it) },
                    onFailure = {
                        MusicSearchState.Error(
                            it.message ?: "Search failed — check your connection.",
                        )
                    },
                )
            }
        }
    }

    fun play(track: Song) {
        playingId = track.videoId
        playError = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { app.streamResolver.resolve(track.videoId) }
            }
            result.fold(
                onSuccess = { stream ->
                    app.playerManager.playStream(
                        stream = stream,
                        title = track.title,
                        artist = track.artist,
                        videoId = track.videoId,
                        thumbnailUrl = track.thumbnailUrl,
                    )
                },
                onFailure = { error ->
                    playError = error.message
                        ?: "Couldn't play that song right now."
                    playingId = null
                },
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Music") })
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search YouTube Music") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { search() },
                enabled = query.isNotBlank() && state != MusicSearchState.Loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Search")
            }
            playError?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            when (val current = state) {
                is MusicSearchState.Idle -> {
                    Text(
                        text = "Search YouTube Music and play right here in Box — " +
                            "no sign-in needed.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                is MusicSearchState.Loading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is MusicSearchState.Ok -> {
                    if (current.tracks.isEmpty()) {
                        Text(
                            text = "No songs matched that search.",
                            modifier = Modifier.padding(16.dp),
                        )
                    } else {
                        LazyColumn {
                            items(current.tracks, key = { it.videoId }) { track ->
                                MusicTrackRow(
                                    track = track,
                                    isPlaying = playingId == track.videoId,
                                    isLoading = playingId == track.videoId &&
                                        playerState.isLoading,
                                    onPlay = { play(track) },
                                )
                            }
                        }
                    }
                }
                is MusicSearchState.Error -> {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Couldn't search: ${current.message}",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { search() }) { Text("Retry") }
                    }
                }
            }
        }
        if (playerState.videoId.isNotEmpty()) {
            NowPlayingBar(
                state = playerState,
                onTogglePlay = { app.playerManager.togglePlayPause() },
            )
        }
    }
}

@Composable
private fun MusicTrackRow(
    track: Song,
    isPlaying: Boolean,
    isLoading: Boolean,
    onPlay: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (track.thumbnailUrl != null) {
            AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
        } else {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
        IconButton(onClick = { openInYtMusic(context, track.videoId) }) {
            Icon(
                imageVector = Icons.Filled.OpenInNew,
                contentDescription = "Open in YT Music",
            )
        }
    }
}
