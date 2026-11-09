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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ppp65isking.box.BoxApp
import com.ppp65isking.box.data.GoogleClient
import com.ppp65isking.box.data.OAuthService
import com.ppp65isking.box.data.ai.AiTask
import com.ppp65isking.box.data.ai.ChatMessage
import com.ppp65isking.box.data.ai.parseReplies
import com.ppp65isking.box.data.buildRawMessage
import com.ppp65isking.box.data.openUrl
import com.ppp65isking.box.model.CalendarEvent
import com.ppp65isking.box.model.DriveFile
import com.ppp65isking.box.model.GmailFullMessage
import com.ppp65isking.box.model.GmailMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The Google hub's three tabs. The bottom bar in MainActivity shows
 *  these (a Google-style bar replaces the Box bar while the hub is
 *  open); the tab state lives there so the bar and the screen agree. */
enum class GoogleTab(val key: String, val label: String, val icon: ImageVector) {
    MAIL("mail", "Email", Icons.Filled.Mail),
    CALENDAR("calendar", "Calendar", Icons.Filled.CalendarMonth),
    DRIVE("drive", "Drive", Icons.Filled.Folder),
}

private sealed interface MailView {
    data object List : MailView
    data class Reader(val messageId: String) : MailView
    data class Reply(val messageId: String) : MailView
    data object Compose : MailView
}

private sealed interface GoogleState {
    data object Idle : GoogleState
    data object Loading : GoogleState
    data class Mail(val messages: List<GmailMessage>) : GoogleState
    data class Events(val events: List<CalendarEvent>) : GoogleState
    data class Files(val files: List<DriveFile>) : GoogleState
    data class Error(val message: String) : GoogleState
}

private const val DOCS_MIME = "application/vnd.google-apps.document"
private const val SLIDES_MIME = "application/vnd.google-apps.presentation"

/** The Google hub — a mini Google app: real email (read, reply,
 *  compose), Calendar (read-only) and Drive (browse, search,
 *  folders, read-only), behind the one Google sign-in.
 *
 *  Replying needs the gmail.send scope, which the old read-only
 *  grant didn't have — after installing this build the user has to
 *  tap Connect again once (Google re-asks for the full scope list).
 *  Until then, sending answers with a plain "connect again" message
 *  instead of failing silently.
 *
 *  The back button top-left returns to Home; the bottom bar is the
 *  Google-style Email / Calendar / Drive bar while this hub is open. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleScreen(
    tab: GoogleTab,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as BoxApp
    val scope = rememberCoroutineScope()

    // Refreshes whenever a sign-in or sign-out lands.
    val oauthVersion by app.oauthVersion.collectAsState()
    val configured = app.oauthStore.isConfigured(OAuthService.GOOGLE)
    val signedIn = app.oauthStore.isSignedIn(OAuthService.GOOGLE)
    val accountLabel = app.oauthStore.accountLabel(OAuthService.GOOGLE)
    val lastError by app.lastOAuthError.collectAsState()

    var state by remember { mutableStateOf<GoogleState>(GoogleState.Idle) }
    var mailQuery by remember { mutableStateOf("") }
    var driveFilter by remember { mutableStateOf<String?>(null) }
    var driveQuery by remember { mutableStateOf("") }
    var driveFolderId by remember { mutableStateOf<String?>(null) }
    var driveTrail by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    // Mail pagination: the list grows with "Load more" instead of a
    // short fixed list. nextToken is null on the last page.
    var mailItems by remember { mutableStateOf<List<GmailMessage>>(emptyList()) }
    var mailNextToken by remember { mutableStateOf<String?>(null) }
    var mailLoadingMore by remember { mutableStateOf(false) }

    // Mail sub-views: list, reader, reply composer, new composer.
    var mailView by remember { mutableStateOf<MailView>(MailView.List) }
    var fullMessage by remember { mutableStateOf<GmailFullMessage?>(null) }
    var readerLoading by remember { mutableStateOf(false) }
    var readerError by remember { mutableStateOf<String?>(null) }
    var summary by remember { mutableStateOf<String?>(null) }
    var summaryEngine by remember { mutableStateOf<String?>(null) }
    var summaryLoading by remember { mutableStateOf(false) }

    // Composer state.
    var composeTo by remember { mutableStateOf("") }
    var composeSubject by remember { mutableStateOf("") }
    var composeBody by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var suggestionsLoading by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var sendError by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    suspend fun tokenOrNull(): String? = app.accessToken(OAuthService.GOOGLE)

    fun loadMail(append: Boolean = false) {
        val currentQuery = mailQuery
        val pageToken = if (append) mailNextToken else null
        if (append) {
            mailLoadingMore = true
        } else {
            notice = null
            state = GoogleState.Loading
        }
        scope.launch {
            val token = tokenOrNull()
            if (token == null) {
                mailLoadingMore = false
                state = GoogleState.Error("Your Google session ended — connect again.")
                return@launch
            }
            when (val r = withContext(Dispatchers.IO) {
                app.googleClient.messages(token, currentQuery.ifBlank { null }, 25, pageToken)
            }) {
                is GoogleClient.Result.Ok -> {
                    mailItems = if (append) mailItems + r.value.messages else r.value.messages
                    mailNextToken = r.value.nextPageToken
                    mailLoadingMore = false
                    state = GoogleState.Mail(mailItems)
                }
                is GoogleClient.Result.Unauthorized -> {
                    mailLoadingMore = false
                    state = GoogleState.Error("Google said the sign-in is stale — connect again.")
                }
                is GoogleClient.Result.Error -> {
                    mailLoadingMore = false
                    state = GoogleState.Error("Couldn't load mail: ${r.message}")
                }
            }
        }
    }

    fun load() {
        when (tab) {
            GoogleTab.MAIL -> loadMail()
            GoogleTab.CALENDAR -> {
                state = GoogleState.Loading
                scope.launch {
                    val token = tokenOrNull()
                    if (token == null) {
                        state = GoogleState.Error("Your Google session ended — connect again.")
                        return@launch
                    }
                    state = when (val r = withContext(Dispatchers.IO) {
                        app.googleClient.events(token)
                    }) {
                        is GoogleClient.Result.Ok -> GoogleState.Events(r.value)
                        is GoogleClient.Result.Unauthorized -> GoogleState.Error(
                            "Google said the sign-in is stale — connect again.",
                        )
                        is GoogleClient.Result.Error -> GoogleState.Error(
                            "Couldn't load events: ${r.message}",
                        )
                    }
                }
            }
            GoogleTab.DRIVE -> {
                state = GoogleState.Loading
                scope.launch {
                    val token = tokenOrNull()
                    if (token == null) {
                        state = GoogleState.Error("Your Google session ended — connect again.")
                        return@launch
                    }
                    val filter = driveFilter
                    val query = driveQuery
                    val folder = driveFolderId
                    state = when (val r = withContext(Dispatchers.IO) {
                        app.googleClient.driveFiles(
                            token,
                            filter,
                            query.ifBlank { null },
                            folder,
                        )
                    }) {
                        is GoogleClient.Result.Ok -> GoogleState.Files(r.value)
                        is GoogleClient.Result.Unauthorized -> GoogleState.Error(
                            "Google said the sign-in is stale — connect again.",
                        )
                        is GoogleClient.Result.Error -> GoogleState.Error(
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
            mailView = MailView.List
            load()
        }
    }

    // Open a message to read it.
    LaunchedEffect(mailView) {
        val view = mailView
        if (view is MailView.Reader) {
            if (fullMessage?.id == view.messageId) return@LaunchedEffect
            readerLoading = true
            readerError = null
            summary = null
            summaryEngine = null
            val token = tokenOrNull()
            if (token == null) {
                readerLoading = false
                readerError = "Your Google session ended — connect again."
                return@LaunchedEffect
            }
            when (val r = withContext(Dispatchers.IO) {
                app.googleClient.message(token, view.messageId)
            }) {
                is GoogleClient.Result.Ok -> {
                    fullMessage = r.value
                    readerLoading = false
                }
                is GoogleClient.Result.Unauthorized -> {
                    readerLoading = false
                    readerError = "Google said the sign-in is stale — connect again."
                }
                is GoogleClient.Result.Error -> {
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
        suggestions = emptyList()
        sendError = null
        mailView = MailView.Reply(full.id)
    }

    fun openCompose() {
        composeTo = ""
        composeSubject = ""
        composeBody = ""
        suggestions = emptyList()
        sendError = null
        mailView = MailView.Compose
    }

    fun sendMail() {
        val to = composeTo.trim()
        val subject = composeSubject.trim()
        val body = composeBody.trim()
        if (to.isBlank() || body.isBlank()) {
            sendError = "Add who it's to and what it says first."
            return
        }
        val replyTo = if (mailView is MailView.Reply) fullMessage else null
        sending = true
        sendError = null
        scope.launch {
            val token = tokenOrNull()
            if (token == null) {
                sending = false
                sendError = "Your Google session ended — connect again."
                return@launch
            }
            val raw = buildRawMessage(
                to = to,
                subject = subject,
                body = body,
                inReplyTo = replyTo?.messageId,
                references = replyTo?.references,
            )
            val result = withContext(Dispatchers.IO) {
                app.googleClient.sendMessage(token, raw, replyTo?.threadId)
            }
            sending = false
            when (result) {
                is GoogleClient.Result.Ok -> {
                    notice = "Sent."
                    mailView = MailView.List
                    composeTo = ""
                    composeSubject = ""
                    composeBody = ""
                    suggestions = emptyList()
                    loadMail()
                }
                is GoogleClient.Result.Unauthorized -> {
                    sendError = "Google said the sign-in is stale — connect again."
                }
                is GoogleClient.Result.Error -> sendError = result.message
            }
        }
    }

    fun summarizeEmail() {
        val full = fullMessage ?: return
        if (summaryLoading) return
        summaryLoading = true
        scope.launch {
            val result = withContext(Dispatchers.Default) {
                app.aiRouter.complete(
                    AiTask.Summarize(
                        listOf("Subject: ${full.subject}", full.body.take(4000)),
                    ),
                )
            }
            summary = result.text
            summaryEngine = result.engineLabel
            summaryLoading = false
        }
    }

    fun suggestReplies() {
        val full = fullMessage ?: return
        if (suggestionsLoading) return
        suggestionsLoading = true
        scope.launch {
            val result = withContext(Dispatchers.Default) {
                app.aiRouter.complete(
                    AiTask.ReplyDraft(
                        listOf(ChatMessage(full.body.take(4000), false)),
                    ),
                )
            }
            suggestions = parseReplies(result.text)
            suggestionsLoading = false
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
                text = "Google",
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
                service = OAuthService.GOOGLE,
                configured = configured,
                signedIn = signedIn,
                accountLabel = accountLabel,
                onConnect = { scope.launch { app.startGoogleLogin(context) } },
                onSignOut = { app.signOut(OAuthService.GOOGLE) },
            )
            lastError?.let { message ->
                Spacer(modifier = Modifier.height(8.dp))
                OAuthErrorCard(
                    message = message,
                    onDismiss = { app.clearOAuthError() },
                )
            }
            if (signedIn && tab == GoogleTab.MAIL && mailView is MailView.List) {
                Spacer(modifier = Modifier.height(12.dp))
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { loadMail() },
                        enabled = state != GoogleState.Loading,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Search")
                    }
                    OutlinedButton(
                        onClick = { openCompose() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Compose")
                    }
                }
                notice?.let { text ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (signedIn && tab == GoogleTab.DRIVE) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = driveQuery,
                    onValueChange = { driveQuery = it },
                    placeholder = { Text("Search Drive by name") },
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = null)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { load() },
                        enabled = state != GoogleState.Loading,
                    ) {
                        Text("Search")
                    }
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
                if (driveTrail.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = {
                                driveTrail = driveTrail.dropLast(1)
                                driveFolderId = driveTrail.lastOrNull()?.first
                                load()
                            },
                        ) {
                            Text("← Up")
                        }
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
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (!signedIn) {
            Text(
                text = "Email, Calendar and Drive live here once an account " +
                    "is connected. Replying needs the send permission, so " +
                    "if you connected before this update, tap Connect " +
                    "again to allow it.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            return@Column
        }
        when (tab) {
            GoogleTab.MAIL -> MailTab(
                view = mailView,
                state = state,
                mailNextToken = mailNextToken,
                mailLoadingMore = mailLoadingMore,
                onOpenMessage = { mailView = MailView.Reader(it) },
                onLoadMore = { loadMail(append = true) },
                onRetry = { loadMail() },
                fullMessage = fullMessage,
                readerLoading = readerLoading,
                readerError = readerError,
                summary = summary,
                summaryEngine = summaryEngine,
                summaryLoading = summaryLoading,
                onSummarize = { summarizeEmail() },
                onBackToList = { mailView = MailView.List },
                onReply = { openReply() },
                composeTo = composeTo,
                composeSubject = composeSubject,
                composeBody = composeBody,
                onComposeToChange = { composeTo = it },
                onComposeSubjectChange = { composeSubject = it },
                onComposeBodyChange = { composeBody = it },
                suggestions = suggestions,
                suggestionsLoading = suggestionsLoading,
                onSuggestReplies = { suggestReplies() },
                sending = sending,
                sendError = sendError,
                onSend = { sendMail() },
                onCancelCompose = { mailView = MailView.List },
            )
            GoogleTab.CALENDAR -> when (val current = state) {
                is GoogleState.Loading -> LoadingRow()
                is GoogleState.Events -> {
                    if (current.events.isEmpty()) {
                        Text(
                            text = "Nothing on your calendar in the next 7 days.",
                            modifier = Modifier.padding(16.dp),
                        )
                    } else {
                        LazyColumn {
                            items(
                                current.events,
                                key = { it.summary + (it.start ?: "") },
                            ) { event ->
                                EventRow(event = event)
                            }
                        }
                    }
                }
                is GoogleState.Error -> ErrorRow(current.message) { load() }
                else -> LoadingRow()
            }
            GoogleTab.DRIVE -> when (val current = state) {
                is GoogleState.Loading -> LoadingRow()
                is GoogleState.Files -> {
                    if (current.files.isEmpty()) {
                        Text(
                            text = "No files here.",
                            modifier = Modifier.padding(16.dp),
                        )
                    } else {
                        LazyColumn {
                            items(current.files, key = { it.id }) { file ->
                                FileRow(
                                    file = file,
                                    onOpenFolder = {
                                        driveTrail = driveTrail + (file.id to file.name)
                                        driveFolderId = file.id
                                        load()
                                    },
                                )
                            }
                        }
                    }
                }
                is GoogleState.Error -> ErrorRow(current.message) { load() }
                else -> LoadingRow()
            }
        }
    }
}

@Composable
private fun LoadingRow() {
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
private fun ErrorRow(message: String, onRetry: () -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}

/** The Email tab: list, reader, reply composer, new composer. */
@Composable
private fun MailTab(
    view: MailView,
    state: GoogleState,
    mailNextToken: String?,
    mailLoadingMore: Boolean,
    onOpenMessage: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    fullMessage: GmailFullMessage?,
    readerLoading: Boolean,
    readerError: String?,
    summary: String?,
    summaryEngine: String?,
    summaryLoading: Boolean,
    onSummarize: () -> Unit,
    onBackToList: () -> Unit,
    onReply: () -> Unit,
    composeTo: String,
    composeSubject: String,
    composeBody: String,
    onComposeToChange: (String) -> Unit,
    onComposeSubjectChange: (String) -> Unit,
    onComposeBodyChange: (String) -> Unit,
    suggestions: List<String>,
    suggestionsLoading: Boolean,
    onSuggestReplies: () -> Unit,
    sending: Boolean,
    sendError: String?,
    onSend: () -> Unit,
    onCancelCompose: () -> Unit,
) {
    when (view) {
        is MailView.List -> when (val current = state) {
            is GoogleState.Idle -> LoadingRow()
            is GoogleState.Loading -> LoadingRow()
            is GoogleState.Mail -> {
                if (current.messages.isEmpty()) {
                    Text(
                        text = "No mail matched that search.",
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    LazyColumn {
                        items(current.messages, key = { it.id }) { message ->
                            MailRow(
                                message = message,
                                onClick = { onOpenMessage(message.id) },
                            )
                        }
                        if (mailNextToken != null) {
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
                    }
                }
            }
            is GoogleState.Error -> ErrorRow(current.message, onRetry)
            else -> LoadingRow()
        }
        is MailView.Reader -> {
            if (readerLoading) {
                LoadingRow()
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
                    full.date?.let { date ->
                        Text(
                            text = date,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onSummarize,
                            enabled = !summaryLoading,
                        ) {
                            Text(if (summaryLoading) "Summarizing…" else "Summarize")
                        }
                        Button(onClick = onReply) { Text("Reply") }
                    }
                    summary?.let { text ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        summaryEngine?.let { engine ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "AI: $engine",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = full.body.ifBlank { "(No readable text in this message.)" },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
        is MailView.Reply, MailView.Compose -> {
            val isReply = view is MailView.Reply
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
                if (isReply) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onSuggestReplies,
                        enabled = !suggestionsLoading,
                    ) {
                        Text(
                            if (suggestionsLoading) "Thinking…" else "Suggest replies",
                        )
                    }
                    if (suggestions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        suggestions.forEach { suggestion ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onComposeBodyChange(suggestion) }
                                    .padding(vertical = 8.dp),
                            ) {
                                Text(
                                    text = suggestion,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
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
private fun MailRow(message: GmailMessage, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
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
private fun FileRow(file: DriveFile, onOpenFolder: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (file.isFolder) {
                    onOpenFolder()
                } else {
                    file.webViewLink?.let { openUrl(context, it) }
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
            text = listOfNotNull(
                file.mimeType?.substringAfterLast("."),
                file.modifiedTime?.substringBefore("T"),
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
