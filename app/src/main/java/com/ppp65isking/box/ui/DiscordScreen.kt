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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.ppp65isking.box.ui.components.BoxHeader
import com.ppp65isking.box.ui.components.OAuthErrorCard
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
import com.ppp65isking.box.data.DiscordClient
import com.ppp65isking.box.data.OAuthService
import com.ppp65isking.box.data.launchOAuth
import com.ppp65isking.box.model.DiscordChannel
import com.ppp65isking.box.model.DiscordGuild
import com.ppp65isking.box.model.DiscordMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface DiscordListState<out T> {
    data object Loading : DiscordListState<Nothing>
    data class Ok<T>(val value: T) : DiscordListState<T>
    data class Error(val message: String) : DiscordListState<Nothing>
    /** Discord refused this call for a user sign-in (see DiscordClient). */
    data class Restricted(val message: String) : DiscordListState<Nothing>
}

private const val MESSAGES_RESTRICTED_TEXT =
    "Discord doesn't let a user sign-in read channel messages through " +
        "its API — that is a Discord rule, not a Box bug. Servers and " +
        "channels still work; messages need Discord's own app."

/** Your Discord servers and channels, behind a user sign-in. Read-only —
 *  Box never sends messages. Channel messages are a known Discord
 *  limit: see [MESSAGES_RESTRICTED_TEXT]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscordScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as BoxApp
    val scope = rememberCoroutineScope()

    val oauthVersion by app.oauthVersion.collectAsState()
    val configured = app.oauthStore.isConfigured(OAuthService.DISCORD)
    val signedIn = app.oauthStore.isSignedIn(OAuthService.DISCORD)
    val accountLabel = app.oauthStore.accountLabel(OAuthService.DISCORD)
    val lastError by app.lastOAuthError.collectAsState()

    var guildsState by remember {
        mutableStateOf<DiscordListState<List<DiscordGuild>>>(DiscordListState.Loading)
    }
    var channelsState by remember {
        mutableStateOf<DiscordListState<List<DiscordChannel>>>(DiscordListState.Loading)
    }
    var messagesState by remember {
        mutableStateOf<DiscordListState<List<DiscordMessage>>>(DiscordListState.Loading)
    }
    var selectedGuild by remember { mutableStateOf<DiscordGuild?>(null) }
    var selectedChannel by remember { mutableStateOf<DiscordChannel?>(null) }

    fun loadGuilds() {
        guildsState = DiscordListState.Loading
        scope.launch {
            val token = app.accessToken(OAuthService.DISCORD)
            if (token == null) {
                guildsState = DiscordListState.Error(
                    "Your Discord session ended — connect again.",
                )
                return@launch
            }
            guildsState = when (val r = withContext(Dispatchers.IO) {
                app.discordClient.guilds(token)
            }) {
                is DiscordClient.Result.Ok -> DiscordListState.Ok(r.value)
                is DiscordClient.Result.Unauthorized -> DiscordListState.Error(
                    "Discord said the sign-in is stale — connect again.",
                )
                is DiscordClient.Result.Restricted -> DiscordListState.Restricted(
                    r.message,
                )
                is DiscordClient.Result.Error -> DiscordListState.Error(
                    "Couldn't load servers: ${r.message}",
                )
            }
        }
    }

    fun loadChannels(guild: DiscordGuild) {
        selectedGuild = guild
        selectedChannel = null
        channelsState = DiscordListState.Loading
        scope.launch {
            val token = app.accessToken(OAuthService.DISCORD) ?: return@launch
            channelsState = when (val r = withContext(Dispatchers.IO) {
                app.discordClient.channels(token, guild.id)
            }) {
                is DiscordClient.Result.Ok -> DiscordListState.Ok(r.value)
                is DiscordClient.Result.Unauthorized -> DiscordListState.Error(
                    "Discord said the sign-in is stale — connect again.",
                )
                is DiscordClient.Result.Restricted -> DiscordListState.Restricted(
                    r.message,
                )
                is DiscordClient.Result.Error -> DiscordListState.Error(
                    "Couldn't load channels: ${r.message}",
                )
            }
        }
    }

    fun loadMessages(channel: DiscordChannel) {
        selectedChannel = channel
        messagesState = DiscordListState.Loading
        scope.launch {
            val token = app.accessToken(OAuthService.DISCORD) ?: return@launch
            messagesState = when (val r = withContext(Dispatchers.IO) {
                app.discordClient.messages(token, channel.id)
            }) {
                is DiscordClient.Result.Ok -> DiscordListState.Ok(r.value)
                is DiscordClient.Result.Unauthorized -> DiscordListState.Error(
                    "Discord said the sign-in is stale — connect again.",
                )
                is DiscordClient.Result.Restricted -> DiscordListState.Restricted(
                    MESSAGES_RESTRICTED_TEXT,
                )
                is DiscordClient.Result.Error -> DiscordListState.Error(
                    "Couldn't load messages: ${r.message}",
                )
            }
        }
    }

    // Reload the server list whenever the auth state changes — e.g. right
    // after the user comes back from signing in.
    LaunchedEffect(oauthVersion) {
        if (signedIn) loadGuilds()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BoxHeader("Discord")
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            OAuthStatusCard(
                service = OAuthService.DISCORD,
                configured = configured,
                signedIn = signedIn,
                accountLabel = accountLabel,
                onConnect = {
                    app.clearOAuthError()
                    launchOAuth(context, OAuthService.DISCORD, app.oauthStore)
                },
                onSignOut = { app.signOut(OAuthService.DISCORD) },
            )
            lastError?.let { message ->
                Spacer(modifier = Modifier.height(8.dp))
                OAuthErrorCard(
                    message = message,
                    onDismiss = { app.clearOAuthError() },
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (!signedIn) {
            Text(
                text = "Your servers and channels live here once an account " +
                    "is connected. Box reads only — it never sends messages.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            return@Column
        }
        val guild = selectedGuild
        val channel = selectedChannel
        when {
            channel != null && guild != null -> {
                BackRow(label = "# ${channel.name}") {
                    selectedChannel = null
                }
                MessageList(state = messagesState) { loadMessages(channel) }
            }
            guild != null -> {
                BackRow(label = guild.name) { selectedGuild = null }
                ChannelList(state = channelsState, onChannel = { loadMessages(it) }) {
                    loadChannels(guild)
                }
            }
            else -> {
                GuildList(state = guildsState, onGuild = { loadChannels(it) }) {
                    loadGuilds()
                }
            }
        }
    }
}

@Composable
private fun BackRow(label: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onBack() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "← Back",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun <T> LoadingOrError(
    state: DiscordListState<T>,
    onRetry: () -> Unit,
    content: @Composable (T) -> Unit,
) {
    when (state) {
        is DiscordListState.Loading -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
        }
        is DiscordListState.Ok -> content(state.value)
        is DiscordListState.Error -> {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onRetry) { Text("Retry") }
            }
        }
        is DiscordListState.Restricted -> {
            Text(
                text = state.message,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun GuildList(
    state: DiscordListState<List<DiscordGuild>>,
    onGuild: (DiscordGuild) -> Unit,
    onRetry: () -> Unit,
) {
    LoadingOrError(state = state, onRetry = onRetry) { guilds ->
        if (guilds.isEmpty()) {
            Text(
                text = "You're not in any servers.",
                modifier = Modifier.padding(16.dp),
            )
            return@LoadingOrError
        }
        LazyColumn {
            items(guilds, key = { it.id }) { guild ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onGuild(guild) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (guild.iconUrl != null) {
                        AsyncImage(
                            model = guild.iconUrl,
                            contentDescription = guild.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Text(
                        text = guild.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelList(
    state: DiscordListState<List<DiscordChannel>>,
    onChannel: (DiscordChannel) -> Unit,
    onRetry: () -> Unit,
) {
    LoadingOrError(state = state, onRetry = onRetry) { channels ->
        if (channels.isEmpty()) {
            Text(
                text = "No text channels in this server.",
                modifier = Modifier.padding(16.dp),
            )
            return@LoadingOrError
        }
        LazyColumn {
            items(channels, key = { it.id }) { channel ->
                Text(
                    text = "# ${channel.name}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onChannel(channel) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MessageList(
    state: DiscordListState<List<DiscordMessage>>,
    onRetry: () -> Unit,
) {
    LoadingOrError(state = state, onRetry = onRetry) { messages ->
        if (messages.isEmpty()) {
            Text(
                text = "No messages here.",
                modifier = Modifier.padding(16.dp),
            )
            return@LoadingOrError
        }
        LazyColumn {
            items(messages, key = { it.id }) { message ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = message.authorName,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    if (message.content.isNotBlank()) {
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    message.timestamp?.let { timestamp ->
                        Text(
                            text = timestamp.take(10) + " " + timestamp
                                .substringAfter("T")
                                .take(5),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
