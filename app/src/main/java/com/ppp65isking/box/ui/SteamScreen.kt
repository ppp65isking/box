package com.ppp65isking.box.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import com.ppp65isking.box.ui.components.BoxHeader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ppp65isking.box.BoxApp
import com.ppp65isking.box.data.SteamClient
import com.ppp65isking.box.data.SteamGame
import com.ppp65isking.box.data.SteamPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class SteamData(
    val profile: SteamPlayer,
    val games: List<SteamGame>,
    val friends: List<SteamPlayer>,
)

private sealed interface SteamState {
    data object Idle : SteamState
    data object Loading : SteamState
    data class Ok(val data: SteamData) : SteamState
    data class Error(val message: String) : SteamState
}

/** Your Steam profile, library, and friends — read-only. Steam needs
 *  your own Web API key and SteamID64, both typed in here once; the key
 *  stays on this device and is never shared. */
@Composable
fun SteamScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as BoxApp
    val scope = rememberCoroutineScope()

    val store = app.steamStore
    val client = app.steamClient

    var configured by remember { mutableStateOf(store.isConfigured) }
    var state by remember { mutableStateOf<SteamState>(SteamState.Idle) }

    fun load() {
        val key = store.apiKey ?: return
        val id = store.steamId ?: return
        state = SteamState.Loading
        scope.launch {
            val profile = when (
                val r = withContext(Dispatchers.IO) { client.playerSummary(key, id) }
            ) {
                is SteamClient.Result.Ok -> r.value
                is SteamClient.Result.Unauthorized -> {
                    state = SteamState.Error(
                        "Steam said the API key isn't right — check it and reconnect.",
                    )
                    return@launch
                }
                is SteamClient.Result.Forbidden -> {
                    state = SteamState.Error(
                        "Steam refused this key (403) — check the key and " +
                            "that your profile is public, then reconnect.",
                    )
                    return@launch
                }
                is SteamClient.Result.Error -> {
                    state = SteamState.Error("Couldn't load your Steam profile: ${r.message}")
                    return@launch
                }
            }
            val games = when (
                val r = withContext(Dispatchers.IO) { client.ownedGames(key, id) }
            ) {
                is SteamClient.Result.Ok -> r.value
                is SteamClient.Result.Unauthorized -> {
                    state = SteamState.Error(
                        "Steam said the API key isn't right — check it and reconnect.",
                    )
                    return@launch
                }
                is SteamClient.Result.Forbidden -> {
                    state = SteamState.Error(
                        "Steam refused this request (403) — check that your " +
                            "profile and game details are public.",
                    )
                    return@launch
                }
                is SteamClient.Result.Error -> {
                    state = SteamState.Error("Couldn't load your games: ${r.message}")
                    return@launch
                }
            }
            // Friends are best-effort: a private friends list shouldn't
            // wipe out the profile and games that did load.
            val friends = when (
                val r = withContext(Dispatchers.IO) { client.friends(key, id) }
            ) {
                is SteamClient.Result.Ok -> r.value
                else -> emptyList()
            }
            state = SteamState.Ok(SteamData(profile, games, friends))
        }
    }

    fun disconnect() {
        store.clear()
        state = SteamState.Idle
        configured = false
    }

    LaunchedEffect(configured) {
        if (configured) load()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BoxHeader("Steam")
        if (!configured) {
            SteamSetupCard(
                onSave = { key, steamId ->
                    store.save(key, steamId)
                    configured = true
                },
            )
            return@Column
        }
        when (val current = state) {
            is SteamState.Idle -> {
                Text(text = "Loading…", modifier = Modifier.padding(16.dp))
            }
            is SteamState.Loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                }
            }
            is SteamState.Error -> {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = current.message,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        Button(onClick = { load() }) { Text("Retry") }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(onClick = { disconnect() }) {
                            Text("Disconnect")
                        }
                    }
                }
            }
            is SteamState.Ok -> {
                val data = current.data
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        ProfileHeader(player = data.profile)
                    }
                    item {
                        SectionTitle("Your games")
                    }
                    if (data.games.isEmpty()) {
                        item {
                            Text(
                                text = "No games to show — your Steam library " +
                                    "may be set to private.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                    items(data.games, key = { it.appId }) { game ->
                        GameRow(game = game)
                    }
                    item {
                        SectionTitle("Friends online")
                    }
                    val online = data.friends.filter { it.state != 0 }
                    if (online.isEmpty()) {
                        item {
                            Text(
                                text = "No friends online right now.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                    items(online, key = { it.steamId }) { friend ->
                        FriendRow(player = friend)
                    }
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        ) {
                            OutlinedButton(onClick = { load() }) {
                                Text("Refresh")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(onClick = { disconnect() }) {
                                Text("Disconnect")
                            }
                        }
                        Spacer(modifier = Modifier.height(72.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SteamSetupCard(onSave: (key: String, steamId: String) -> Unit) {
    var apiKey by remember { mutableStateOf("") }
    var steamId by remember { mutableStateOf("") }
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Connect your Steam account",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Steam needs your own Web API key and SteamID64. " +
                        "Get a free key at steamcommunity.com/dev/apikey — " +
                        "your SteamID64 is the number in your Steam profile " +
                        "address. Both stay on this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("Steam Web API key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = steamId,
                    onValueChange = { steamId = it },
                    label = { Text("SteamID64") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { onSave(apiKey.trim(), steamId.trim()) },
                    enabled = apiKey.isNotBlank() && steamId.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Save and connect")
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Your profile, games, and friends live here once " +
                "Steam is connected.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProfileHeader(player: SteamPlayer) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (player.avatarUrl != null) {
            AsyncImage(
                model = player.avatarUrl,
                contentDescription = player.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = player.name.firstOrNull()?.toString() ?: "?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = player.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = player.currentGame?.let { "Playing $it" }
                    ?: stateLabel(player),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GameRow(game: SteamGame) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (game.iconUrl != null) {
            AsyncImage(
                model = game.iconUrl,
                contentDescription = game.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = game.name.firstOrNull()?.toString() ?: "?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = game.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatPlaytime(game.playtimeMinutes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FriendRow(player: SteamPlayer) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (player.avatarUrl != null) {
            AsyncImage(
                model = player.avatarUrl,
                contentDescription = player.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = player.name.firstOrNull()?.toString() ?: "?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = player.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = player.currentGame?.let { "Playing $it" }
                    ?: stateLabel(player),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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

private fun stateLabel(player: SteamPlayer): String = when (player.state) {
    1 -> "Online"
    2 -> "Busy"
    3 -> "Away"
    4 -> "Snooze"
    5 -> "Looking to trade"
    6 -> "Looking to play"
    else -> "Offline"
}

private fun formatPlaytime(minutes: Int): String =
    if (minutes < 60) {
        "$minutes min played"
    } else {
        "%.1f hrs played".format(minutes / 60f)
    }
