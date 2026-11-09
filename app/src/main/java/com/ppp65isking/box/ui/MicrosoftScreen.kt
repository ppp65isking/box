package com.ppp65isking.box.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ppp65isking.box.BoxApp
import com.ppp65isking.box.data.MicrosoftGraphClient
import com.ppp65isking.box.data.OAuthService
import com.ppp65isking.box.data.launchOAuth
import com.ppp65isking.box.data.openUrl
import com.ppp65isking.box.model.MsDriveItem
import com.ppp65isking.box.model.MsEvent
import com.ppp65isking.box.model.MsMailFull
import com.ppp65isking.box.model.MsMailMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The Microsoft hub's tabs. A third tab is still undecided —
 *  for now the hub is Email + Files. */
enum class MsTab(val key: String, val label: String, val icon: ImageVector) {
    MAIL("mail", "Email", Icons.Filled.Mail),
    FILES("files", "Files", Icons.Filled.Folder),
}

private sealed interface MsMailView {
    data object List : MsMailView
    data class Reader(val messageId: String) : MsMailView
    data class Reply(val messageId: String) : MsMailView
    data object Compose : MsMailView
}

private sealed interface MsState {
    data object Idle : MsState
    data object Loading : MsState
    data class Mail(val messages: List<MsMailMessage>) : MsState
    data class Files(val files: List<MsDriveItem>) : MsState
    data class Error(val message: String) : MsState
}

/** The Microsoft hub — a mini Microsoft app: Outlook mail (read,
 *  reply, compose) with the calendar bundled into the Email tab
 *  (Outlook has calendar built in, so the hub mirrors that), and
 *  OneDrive files (browse, search, folders, read-only), behind the
 *  one Microsoft Graph sign-in.
 *
 *  The Graph login is separate from the Google one: it uses
 *  Authorization Code + PKCE against login.microsoftonline.com, with
 *  the redirect URI box://oauth/microsoft registered under "Mobile
 *  and desktop applications" in the Entra app registration. The
 *  client ID is blank until Anthony creates that registration
 *  (SETUP.md §5) — until then this screen says plainly that sign-in
 *  isn't available yet and shows no Connect button, exactly like
 *  Reddit's paused state.
 *
 *  The back button top-left returns to Home; the bottom bar is the
 *  Microsoft-style Email / Files bar while this hub is open. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MicrosoftScreen(
    tab: MsTab,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as BoxApp
    val scope = rememberCoroutineScope()

    // Refreshes whenever a sign-in or sign-out lands.
    val oauthVersion by app.oauthVersion.collectAsState()
    val configured = app.oauthStore.isConfigured(OAuthService.MICROSOFT)
    val signedIn = app.oauthStore.isSignedIn(OAuthService.MICROSOFT)
    val accountLabel = app.oauthStore.accountLabel(OAuthService.MICROSOFT)
    val lastError by app.lastOAuthError.collectAsState()

    var state by remember { mutableStateOf<MsState>(MsState.Idle) }
    var mailQuery by remember { mutableStateOf("") }
    var driveQuery by remember { mutableStateOf("") }
    var driveFolderId by remember { mutableStateOf<String?>(null) }
    var driveTrail by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    // Mail pagination: Graph hands back an @odata.nextLink; null on
    // the last page.
    var mailItems by remember { mutableStateOf<List<MsMailMessage>>(emptyList()) }
    var mailNextLink by remember { mutableStateOf<String?>(null) }
    var mailLoadingMore by remember { mutableStateOf(false) }

    // Calendar, bundled into the Email tab (Outlook-style).
    var events by remember { mutableStateOf<List<MsEvent>>(emptyList()) }
    var eventsLoading by remember { mutableStateOf(false) }

    // Mail sub-views: list, reader, reply composer, new composer.
    var mailView by remember { mutableStateOf<MsMailView>(MsMailView.List) }
    var fullMessage by remember { mutableStateOf<MsMailFull?>(null) }
    var readerLoading by remember { mutableStateOf(false) }
    var readerError by remember { mutableStateOf<String?>(null) }

    // Composer state.
    var composeTo by remember { mutableStateOf("") }
    var composeSubject by remember { mutableStateOf("") }
    var composeBody by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var sendError by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    suspend fun tokenOrNull(): String? = app.accessToken(OAuthService.MICROSOFT)

    fun loadMail(append: Boolean = false) {
        val currentQuery = mailQuery
        val nextLink = if (append) mailNextLink else null
        if (append) {
            mailLoadingMore = true
        } else {
            notice = null
            state = MsState.Loading
        }
        scope.launch {
            val token = tokenOrNull()
            if (token == null) {
                mailLoadingMore = false
                state = MsState.Error("Your Microsoft session ended — connect again.")
                return@launch
            }
            when (val r = withContext(Dispatchers.IO) {
                app.microsoftClient.messages(token, currentQuery.ifBlank { null }, nextLink)
            }) {
                is MicrosoftGraphClient.Result.Ok -> {
                    mailItems = if (append) mailItems + r.value.messages else r.value.messages
                    mailNextLink = r.value.nextLink
                    mailLoadingMore = false
                    state = MsState.Mail(mailItems)
                }
                is MicrosoftGraphClient.Result.Unauthorized -> {
                    mailLoadingMore = false
                    state = MsState.Error("Microsoft said the sign-in is stale — connect again.")
                }
                is MicrosoftGraphClient.Result.Error -> {
                    mailLoadingMore = false
                    state = MsState.Error("Couldn't load mail: ${r.message}")
                }
            }
        }
    }

    fun loadEvents() {
        eventsLoading = true
        scope.launch {
            val token = tokenOrNull()
            if (token == null) {
                eventsLoading = false
                return@launch
            }
            when (val r = withContext(Dispatchers.IO) {
                app.microsoftClient.events(token)
            }) {
                is MicrosoftGraphClient.Result.Ok -> events = r.value
                else -> events = emptyList()
            }
            eventsLoading = false
        }
    }

    fun load() {
        when (tab) {
            MsTab.MAIL -> {
                loadMail()
                loadEvents()
            }
            MsTab.FILES -> {
                state = MsState.Loading
                scope.launch {
                    val token = tokenOrNull()
                    if (token == null) {
                        state = MsState.Error("Your Microsoft session ended — connect again.")
                        return@launch
                    }
                    val query = driveQuery
                    val folder = driveFolderId
                    state = when (val r = withContext(Dispatchers.IO) {
                        app.microsoftClient.driveItems(token, folder, query.ifBlank { null })
                    }) {
                        is MicrosoftGraphClient.Result.Ok -> MsState.Files(r.value)
                        is MicrosoftGraphClient.Result.Unauthorized -> MsState.Error(
                            "Microsoft said the sign-in is stale — connect again.",
                        )
                        is MicrosoftGraphClient.Result.Error -> MsState.Error(
                            "Couldn't load files: ${r.message}",
                        )
                    }
                }
            }
        }
    }

    // Load (or reload) the tab whenever the tab changes or the auth
    // state changes — e.g. right after the user comes back from signing in.
    LaunchedEffect(tab, oauthVersion) {
        if (signedIn) {
            mailView = MsMailView.List
            load()
        }
    }

    // Open a message to read it — and mark it read in Outlook.
    LaunchedEffect(mailView) {
        val view = mailView
        if (view is MsMailView.Reader) {
            if (fullMessage?.id == view.messageId) return@LaunchedEffect
            readerLoading = true
            readerError = null
            val token = tokenOrNull()
            if (token == null) {
                readerLoading = false
                readerError = "Your Microsoft session ended — connect again."
                return@LaunchedEffect
            }
            when (val r = withContext(Dispatchers.IO) {
                app.microsoftClient.message(token, view.messageId)
            }) {
                is MicrosoftGraphClient.Result.Ok -> {
                    fullMessage = r.value
                    readerLoading = false
                    val listed = mailItems.firstOrNull { it.id == view.messageId }
                    if (listed?.isRead == false) {
                        mailItems = mailItems.map {
                            if (it.id == view.messageId) it.copy(isRead = true) else it
                        }
                        state = MsState.Mail(mailItems)
                        withContext(Dispatchers.IO) {
                            app.microsoftClient.markRead(token, view.messageId)
                        }
                    }
                }
                is MicrosoftGraphClient.Result.Unauthorized -> {
                    readerLoading = false
                    readerError = "Microsoft said the sign-in is stale — connect again."
                }
                is MicrosoftGraphClient.Result.Error -> {
                    readerLoading = false
                    readerError = "Couldn't open that message: ${r.message}"
                }
            }
        }
    }

    // Open the reply composer, prefilled from the open message.
    fun openReply() {
        val full = fullMessage ?: return
        composeTo = full.from
        composeSubject = if (full.subject.startsWith("Re:", ignoreCase = true)) {
            full.subject
        } else {
            "Re: ${full.subject}"
        }
        composeBody = ""
        sendError = null
        mailView = MsMailView.Reply(full.id)
    }

    fun openCompose() {
        composeTo = ""
        composeSubject = ""
        composeBody = ""
        sendError = null
        mailView = MsMailView.Compose
    }

    fun sendMail() {
        val to = composeTo.trim()
        val subject = composeSubject.trim()
        val body = composeBody.trim()
        if (to.isBlank() || body.isBlank()) {
            sendError = "Add who it's to and what it says first."
            return
        }
        val replyTo = if (mailView is MsMailView.Reply) fullMessage else null
        sending = true
        sendError = null
        scope.launch {
            val token = tokenOrNull()
            if (token == null) {
                sending = false
                sendError = "Your Microsoft session ended — connect again."
                return@launch
            }
            val result = withContext(Dispatchers.IO) {
                if (replyTo != null) {
                    // Graph's /reply action threads the reply
                    // server-side; the comment is the reply body.
                    app.microsoftClient.reply(token, replyTo.id, body)
                } else {
                    app.microsoftClient.sendMail(token, to, subject, body)
                }
            }
            sending = false
            when (result) {
                is MicrosoftGraphClient.Result.Ok -> {
                    notice = "Sent."
                    mailView = MsMailView.List
                    composeTo = ""
                    composeSubject = ""
                    composeBody = ""
                    loadMail()
                }
                is MicrosoftGraphClient.Result.Unauthorized -> {
                    sendError = "Microsoft said the sign-in is stale — connect again."
                }
                is MicrosoftGraphClient.Result.Error -> sendError = result.message
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Back button top-left on every hub screen — returns to Home.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Back to Home",
                )
            }
            Text(
                text = "Microsoft",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            accountLabel?.let { label ->
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 16.dp),
                )
            }
        }
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            OAuthStatusCard(
                service = OAuthService.MICROSOFT,
                configured = configured,
                signedIn = signedIn,
                accountLabel = accountLabel,
                onConnect = { launchOAuth(context, OAuthService.MICROSOFT, app.oauthStore) },
                onSignOut = { app.signOut(OAuthService.MICROSOFT) },
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
        if (!configured) {
            Text(
                text = "Microsoft sign-in isn't available yet — connect " +
                    "coming soon. Outlook mail, calendar and OneDrive " +
                    "will live here once the app is set up.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            return@Column
        }
        if (!signedIn) {
            Text(
                text = "Outlook mail, calendar and OneDrive live here " +
                    "once an account is connected.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            return@Column
        }
        when (tab) {
            MsTab.MAIL -> MsMailTab(
                state = state,
                view = mailView,
                mailNextLink = mailNextLink,
                mailLoadingMore = mailLoadingMore,
                mailQuery = mailQuery,
                onMailQueryChange = { mailQuery = it },
                onSearch = { loadMail() },
                onOpenMessage = { mailView = MsMailView.Reader(it) },
                onLoadMore = { loadMail(append = true) },
                onRetry = { loadMail() },
                onCompose = { openCompose() },
                events = events,
                eventsLoading = eventsLoading,
                fullMessage = fullMessage,
                readerLoading = readerLoading,
                readerError = readerError,
                onBackToList = { mailView = MsMailView.List },
                onReply = { openReply() },
                composeTo = composeTo,
                composeSubject = composeSubject,
                composeBody = composeBody,
                onComposeToChange = { composeTo = it },
                onComposeSubjectChange = { composeSubject = it },
                onComposeBodyChange = { composeBody = it },
                sending = sending,
                sendError = sendError,
                onSend = { sendMail() },
                onCancelCompose = { mailView = MsMailView.List },
                notice = notice,
            )
            MsTab.FILES -> MsFilesTab(
                state = state,
                driveQuery = driveQuery,
                onDriveQueryChange = { driveQuery = it },
                onSearch = { load() },
                driveTrail = driveTrail,
                onUp = {
                    driveTrail = driveTrail.dropLast(1)
                    driveFolderId = driveTrail.lastOrNull()?.first
                    load()
                },
                onOpenFolder = { file ->
                    driveTrail = driveTrail + (file.id to file.name)
                    driveFolderId = file.id
                    load()
                },
                onRetry = { load() },
            )
        }
    }
}

/** The Email tab: Outlook mail list (with the calendar bundled in
 *  as an "Upcoming" section, Outlook-style), reader, reply composer,
 *  new composer. */
@Composable
private fun MsMailTab(
    state: MsState,
    view: MsMailView,
    mailNextLink: String?,
    mailLoadingMore: Boolean,
    mailQuery: String,
    onMailQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onOpenMessage: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onCompose: () -> Unit,
    events: List<MsEvent>,
    eventsLoading: Boolean,
    fullMessage: MsMailFull?,
    readerLoading: Boolean,
    readerError: String?,
    onBackToList: () -> Unit,
    onReply: () -> Unit,
    composeTo: String,
    composeSubject: String,
    composeBody: String,
    onComposeToChange: (String) -> Unit,
    onComposeSubjectChange: (String) -> Unit,
    onComposeBodyChange: (String) -> Unit,
    sending: Boolean,
    sendError: String?,
    onSend: () -> Unit,
    onCancelCompose: () -> Unit,
    notice: String?,
) {
    when (view) {
        is MsMailView.List -> when (val current = state) {
            is MsState.Idle -> MsLoadingRow()
            is MsState.Loading -> MsLoadingRow()
            is MsState.Mail -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn {
                        // Calendar, bundled into the Email tab.
                        item {
                            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                                OutlinedTextField(
                                    value = mailQuery,
                                    onValueChange = onMailQueryChange,
                                    placeholder = { Text("Search mail") },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Search, contentDescription = null)
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(24.dp),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = onSearch,
                                    enabled = current != MsState.Loading,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Search")
                                }
                                notice?.let { text ->
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Upcoming",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                if (eventsLoading) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                } else if (events.isEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Nothing on your calendar in the next 7 days.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    events.take(5).forEach { event ->
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Column {
                                            Text(
                                                text = event.subject,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = listOfNotNull(
                                                    event.start?.let { formatMsStart(it) },
                                                    event.location?.takeIf { it.isNotBlank() },
                                                ).joinToString(" · "),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                        if (current.messages.isEmpty()) {
                            item {
                                Text(
                                    text = "No mail matched that search.",
                                    modifier = Modifier.padding(16.dp),
                                )
                            }
                        } else {
                            items(current.messages, key = { it.id }) { message ->
                                MsMailRow(
                                    message = message,
                                    onClick = { onOpenMessage(message.id) },
                                )
                            }
                        }
                        if (mailNextLink != null) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    if (mailLoadingMore) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                        )
                                    } else {
                                        OutlinedButton(onClick = onLoadMore) {
                                            Text("Load more")
                                        }
                                    }
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(96.dp)) }
                    }
                    FloatingActionButton(
                        onClick = onCompose,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 88.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Compose",
                        )
                    }
                }
            }
            is MsState.Error -> MsErrorRow(current.message, onRetry)
            else -> MsLoadingRow()
        }
        is MsMailView.Reader -> {
            if (readerLoading) {
                MsLoadingRow()
            } else if (readerError != null) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = readerError,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = onBackToList) {
                        Text("Back to inbox")
                    }
                }
            } else if (fullMessage != null) {
                val full = fullMessage
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                ) {
                    TextButton(onClick = onBackToList) { Text("← Back to inbox") }
                    Text(
                        text = full.subject,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = full.from,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    full.receivedDateTime?.let { date ->
                        Text(
                            text = formatMsStart(date),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = onReply) { Text("Reply") }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = full.body.ifBlank { "(No readable text in this message.)" },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
        is MsMailView.Reply, MsMailView.Compose -> {
            val isReply = view is MsMailView.Reply
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    text = if (isReply) "Reply" else "New mail",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (isReply) {
                    Text(
                        text = "To: $composeTo",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                } else {
                    OutlinedTextField(
                        value = composeTo,
                        onValueChange = onComposeToChange,
                        label = { Text("To") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = composeSubject,
                    onValueChange = onComposeSubjectChange,
                    label = { Text("Subject") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = composeBody,
                    onValueChange = onComposeBodyChange,
                    label = { Text("Message") },
                    minLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
                sendError?.let { error ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onSend,
                        enabled = !sending,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (sending) "Sending…" else "Send")
                    }
                    OutlinedButton(
                        onClick = onCancelCompose,
                        enabled = !sending,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Cancel")
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

/** The Files tab: OneDrive browse, search, folders, read-only. */
@Composable
private fun MsFilesTab(
    state: MsState,
    driveQuery: String,
    onDriveQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    driveTrail: List<Pair<String, String>>,
    onUp: () -> Unit,
    onOpenFolder: (MsDriveItem) -> Unit,
    onRetry: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = driveQuery,
                onValueChange = onDriveQueryChange,
                placeholder = { Text("Search OneDrive by name") },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null)
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onSearch,
                enabled = state != MsState.Loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Search")
            }
            if (driveTrail.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onUp) { Text("← Up") }
                    Text(
                        text = driveTrail.joinToString(" / ") { it.second },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        when (val current = state) {
            is MsState.Loading -> MsLoadingRow()
            is MsState.Files -> {
                if (current.files.isEmpty()) {
                    Text(
                        text = "No files here.",
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    LazyColumn {
                        items(current.files, key = { it.id }) { file ->
                            MsFileRow(file = file, onOpenFolder = onOpenFolder)
                        }
                    }
                }
            }
            is MsState.Error -> MsErrorRow(current.message, onRetry)
            else -> MsLoadingRow()
        }
    }
}

@Composable
private fun MsLoadingRow() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun MsErrorRow(message: String, onRetry: () -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}

/** Outlook-style message row: avatar circle, bold unread, short date. */
@Composable
private fun MsMailRow(message: MsMailMessage, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(msAvatarColor(message.from)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = msSenderInitial(message.from),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = msSenderName(message.from),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (!message.isRead) {
                        FontWeight.Bold
                    } else {
                        FontWeight.Normal
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                message.receivedDateTime?.let { date ->
                    Text(
                        text = formatMsStart(date),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = message.subject,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (!message.isRead) {
                    FontWeight.Bold
                } else {
                    FontWeight.Normal
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            message.bodyPreview?.takeIf { it.isNotBlank() }?.let { preview ->
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MsFileRow(file: MsDriveItem, onOpenFolder: (MsDriveItem) -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (file.isFolder) {
                    onOpenFolder(file)
                } else {
                    file.webUrl?.let { openUrl(context, it) }
                }
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (file.isFolder) {
                Icon(
                    imageVector = Icons.Filled.Folder,
                    contentDescription = "Folder",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = file.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = file.lastModified?.substringBefore("T").orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "2026-11-09T14:30:00Z" → "2:30 PM" when it is today, "Nov 9"
 *  when it is this year, "Nov 9, 2025" otherwise. Falls back to the
 *  raw string when it won't parse. */
private fun formatMsStart(start: String?): String {
    if (start.isNullOrBlank()) return "No time listed"
    return try {
        val instant = java.time.OffsetDateTime.parse(start)
        val now = java.time.OffsetDateTime.now(instant.offset)
        val sameDay = instant.toLocalDate() == now.toLocalDate()
        val sameYear = instant.year == now.year
        val pattern = when {
            sameDay -> "h:mm a"
            sameYear -> "MMM d"
            else -> "MMM d, yyyy"
        }
        instant.format(
            java.time.format.DateTimeFormatter.ofPattern(
                pattern,
                java.util.Locale.getDefault(),
            ),
        )
    } catch (_: Exception) {
        start.substringBefore("T")
    }
}

private fun msSenderName(from: String): String {
    val name = from.substringBefore("<").trim().trim('"')
    return name.ifBlank { from.substringBefore("@") }
}

private fun msSenderInitial(from: String): String =
    msSenderName(from).firstOrNull()?.uppercase() ?: "?"

private val MS_AVATAR_COLORS = listOf(
    Color(0xFFF97316),
    Color(0xFF16A34A),
    Color(0xFF2563EB),
    Color(0xFF00897B),
    Color(0xFFEF6C00),
    Color(0xFFC2185B),
)

private fun msAvatarColor(sender: String): Color =
    MS_AVATAR_COLORS[(sender.hashCode() and Int.MAX_VALUE) % MS_AVATAR_COLORS.size]
