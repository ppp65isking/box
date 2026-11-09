package com.ppp65isking.box.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ppp65isking.box.BoxApp
import com.ppp65isking.box.data.GoogleClient
import com.ppp65isking.box.data.OAuthService
import com.ppp65isking.box.data.launchOAuth
import com.ppp65isking.box.data.openUrl
import com.ppp65isking.box.model.CalendarEvent
import com.ppp65isking.box.model.DriveFile
import com.ppp65isking.box.model.GmailMessage
import com.ppp65isking.box.model.PhotoItem
import kotlinx.coroutines.launch

private enum class GoogleTab(val label: String) {
    MAIL("Mail"),
    CALENDAR("Calendar"),
    DRIVE("Drive"),
    PHOTOS("Photos"),
}

private sealed interface GoogleState {
    data object Idle : GoogleState
    data object Loading : GoogleState
    data class Mail(val messages: List<GmailMessage>) : GoogleState
    data class Events(val events: List<CalendarEvent>) : GoogleState
    data class Files(val files: List<DriveFile>) : GoogleState
    data class Photos(val photos: List<PhotoItem>) : GoogleState
    data class Error(val message: String) : GoogleState
}

private const val DOCS_MIME = "application/vnd.google-apps.document"
private const val SLIDES_MIME = "application/vnd.google-apps.presentation"

/** Gmail + Calendar + Drive (+ Docs/Slides) + Photos, behind one Google
 *  sign-in. Read-only. The account belongs to whoever tapped Connect. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as BoxApp
    val scope = rememberCoroutineScope()

    // Refreshes whenever a sign-in or sign-out lands.
    val oauthVersion by app.oauthVersion.collectAsState()
    val configured = app.oauthStore.isConfigured(OAuthService.GOOGLE)
    val signedIn = app.oauthStore.isSignedIn(OAuthService.GOOGLE)
    val accountLabel = app.oauthStore.accountLabel(OAuthService.GOOGLE)

    var tab by remember { mutableStateOf(GoogleTab.MAIL) }
    var state by remember { mutableStateOf<GoogleState>(GoogleState.Idle) }
    var mailQuery by remember { mutableStateOf("") }
    var driveFilter by remember { mutableStateOf<String?>(null) }

    fun load() {
        val currentTab = tab
        val currentFilter = driveFilter
        val currentQuery = mailQuery
        state = GoogleState.Loading
        scope.launch {
            val token = app.accessToken(OAuthService.GOOGLE)
            if (token == null) {
                state = GoogleState.Error(
                    "Your Google session ended — connect again.",
                )
                return@launch
            }
            state = when (currentTab) {
                GoogleTab.MAIL -> when (val r = app.googleClient.messages(
                    token,
                    currentQuery.ifBlank { null },
                )) {
                    is GoogleClient.Result.Ok -> GoogleState.Mail(r.value)
                    is GoogleClient.Result.Unauthorized -> GoogleState.Error(
                        "Google said the sign-in is stale — connect again.",
                    )
                    is GoogleClient.Result.Error -> GoogleState.Error(
                        "Couldn't load mail: ${r.message}",
                    )
                }
                GoogleTab.CALENDAR -> when (val r = app.googleClient.events(token)) {
                    is GoogleClient.Result.Ok -> GoogleState.Events(r.value)
                    is GoogleClient.Result.Unauthorized -> GoogleState.Error(
                        "Google said the sign-in is stale — connect again.",
                    )
                    is GoogleClient.Result.Error -> GoogleState.Error(
                        "Couldn't load events: ${r.message}",
                    )
                }
                GoogleTab.DRIVE -> when (val r = app.googleClient.driveFiles(
                    token,
                    currentFilter,
                )) {
                    is GoogleClient.Result.Ok -> GoogleState.Files(r.value)
                    is GoogleClient.Result.Unauthorized -> GoogleState.Error(
                        "Google said the sign-in is stale — connect again.",
                    )
                    is GoogleClient.Result.Error -> GoogleState.Error(
                        "Couldn't load files: ${r.message}",
                    )
                }
                GoogleTab.PHOTOS -> when (val r = app.googleClient.photos(token)) {
                    is GoogleClient.Result.Ok -> GoogleState.Photos(r.value)
                    is GoogleClient.Result.Unauthorized -> GoogleState.Error(
                        "Google said the sign-in is stale — connect again.",
                    )
                    is GoogleClient.Result.Error -> GoogleState.Error(
                        "Couldn't load photos: ${r.message}",
                    )
                }
            }
        }
    }

    // Load (or reload) the tab whenever the tab changes or the auth
    // state changes — e.g. right after the user comes back from signing in.
    LaunchedEffect(tab, oauthVersion) {
        if (signedIn) load()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BoxHeader("Google")
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            OAuthStatusCard(
                service = OAuthService.GOOGLE,
                configured = configured,
                signedIn = signedIn,
                accountLabel = accountLabel,
                onConnect = { launchOAuth(context, OAuthService.GOOGLE, app.oauthStore) },
                onSignOut = { app.signOut(OAuthService.GOOGLE) },
            )
            if (signedIn) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GoogleTab.entries.forEach { entry ->
                        if (entry == tab) {
                            Button(onClick = { tab = entry }) { Text(entry.label) }
                        } else {
                            OutlinedButton(onClick = { tab = entry }) {
                                Text(entry.label)
                            }
                        }
                    }
                }
                if (tab == GoogleTab.MAIL) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = mailQuery,
                        onValueChange = { mailQuery = it },
                        placeholder = { Text("Search mail (e.g. from:amazon)") },
                        leadingIcon = {
                            Icon(Icons.Filled.Search, contentDescription = null)
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { load() },
                        enabled = state != GoogleState.Loading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Search")
                    }
                }
                if (tab == GoogleTab.DRIVE) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DriveFilterButton("All", driveFilter == null) {
                            driveFilter = null
                            load()
                        }
                        DriveFilterButton("Docs", driveFilter == DOCS_MIME) {
                            driveFilter = DOCS_MIME
                            load()
                        }
                        DriveFilterButton("Slides", driveFilter == SLIDES_MIME) {
                            driveFilter = SLIDES_MIME
                            load()
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (!signedIn) {
            Text(
                text = "Mail, Calendar, Drive, Docs, Slides and Photos live " +
                    "here once an account is connected.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            return@Column
        }
        when (val current = state) {
            is GoogleState.Idle -> {
                Text(
                    text = "Loading…",
                    modifier = Modifier.padding(16.dp),
                )
            }
            is GoogleState.Loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                }
            }
            is GoogleState.Mail -> {
                if (current.messages.isEmpty()) {
                    Text(
                        text = "No mail matched that search.",
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    LazyColumn {
                        items(current.messages, key = { it.id }) { message ->
                            MailRow(message = message)
                        }
                    }
                }
            }
            is GoogleState.Events -> {
                if (current.events.isEmpty()) {
                    Text(
                        text = "Nothing on your calendar in the next 7 days.",
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    LazyColumn {
                        items(current.events, key = { it.summary + (it.start ?: "") }) { event ->
                            EventRow(event = event)
                        }
                    }
                }
            }
            is GoogleState.Files -> {
                if (current.files.isEmpty()) {
                    Text(
                        text = "No files here.",
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    LazyColumn {
                        items(current.files, key = { it.id }) { file ->
                            FileRow(file = file)
                        }
                    }
                }
            }
            is GoogleState.Photos -> {
                if (current.photos.isEmpty()) {
                    Text(
                        text = "No photos came back. Google's Photos Library " +
                            "limits may only show photos you picked for Box — " +
                            "that is a Google-side limit, not a Box bug.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    LazyColumn {
                        items(current.photos, key = { it.id }) { photo ->
                            PhotoRow(photo = photo)
                        }
                    }
                }
            }
            is GoogleState.Error -> {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = current.message,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { load() }) { Text("Retry") }
                }
            }
        }
    }
}

@Composable
private fun DriveFilterButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(onClick = onClick) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label) }
    }
}

@Composable
private fun MailRow(message: GmailMessage) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = message.subject,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = message.from,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        message.snippet?.takeIf { it.isNotBlank() }?.let { snippet ->
            Text(
                text = snippet,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        message.date?.let { date ->
            Text(
                text = date,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EventRow(event: CalendarEvent) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = event.summary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = formatEventStart(event.start),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        event.location?.takeIf { it.isNotBlank() }?.let { location ->
            Text(
                text = location,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** "2026-11-09T14:30:00-05:00" → "Nov 9, 2:30 PM"; a plain date stays a date. */
private fun formatEventStart(start: String?): String {
    if (start.isNullOrBlank()) return "No time listed"
    if (!start.contains("T")) return start
    return try {
        val instant = java.time.OffsetDateTime.parse(start)
        val date = java.time.format.DateTimeFormatter
            .ofPattern("MMM d, h:mm a", java.util.Locale.getDefault())
        instant.format(date)
    } catch (_: Exception) {
        start.substringBefore("T") + " " + start.substringAfter("T").take(5)
    }
}

@Composable
private fun FileRow(file: DriveFile) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = file.webViewLink != null) {
                file.webViewLink?.let { openUrl(context, it) }
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = file.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = listOfNotNull(
                file.mimeType?.substringAfterLast("."),
                file.modifiedTime?.substringBefore("T"),
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PhotoRow(photo: PhotoItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (photo.baseUrl != null) {
            AsyncImage(
                model = photo.baseUrl + "=w400-h300-c",
                contentDescription = photo.filename,
                modifier = Modifier.size(72.dp),
            )
            Spacer(modifier = Modifier.padding(horizontal = 6.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = photo.filename ?: "(no name)",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            photo.mimeType?.let { mime ->
                Text(
                    text = mime,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
