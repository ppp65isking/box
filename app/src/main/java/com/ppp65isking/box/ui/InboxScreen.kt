package com.ppp65isking.box.ui

import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.ppp65isking.box.ui.components.BoxHeader
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
import androidx.compose.ui.unit.dp
import com.ppp65isking.box.BoxApp
import com.ppp65isking.box.data.NotificationRepository
import com.ppp65isking.box.data.ai.AiTask
import com.ppp65isking.box.data.ai.DetectedEntity
import com.ppp65isking.box.data.ai.extractEntities
import com.ppp65isking.box.data.loadInstalledApps
import com.ppp65isking.box.ui.components.EntityChips
import com.ppp65isking.box.ui.components.NotificationAccessCard
import com.ppp65isking.box.ui.components.NotificationList
import com.ppp65isking.box.ui.components.OnResume
import com.ppp65isking.box.ui.components.isNotificationListenerEnabled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One inbox for every app's notifications, newest first.
 *  Read-only: tapping a notification opens the app it came from.
 *
 *  "Catch me up" runs the AI router over the newest notifications —
 *  Gemini Nano first, then the downloaded on-device model, then the
 *  cloud legs, then a plain list. It runs on tap, while Box is open
 *  (Nano is foreground-only). The "AI: …" label says which model
 *  answered, and phone numbers / links / emails in the digest become
 *  tappable chips. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as BoxApp
    val scope = rememberCoroutineScope()
    val notifications by NotificationRepository.notifications.collectAsState()

    var listenerEnabled by remember { mutableStateOf(false) }
    fun refreshListenerState() {
        listenerEnabled = isNotificationListenerEnabled(context)
    }
    OnResume { refreshListenerState() }
    LaunchedEffect(Unit) { refreshListenerState() }

    var icons by remember { mutableStateOf<Map<String, Drawable?>>(emptyMap()) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            icons = loadInstalledApps(context)
                .associate { it.packageName to it.icon }
        }
    }

    var digest by remember { mutableStateOf<String?>(null) }
    var digestEngine by remember { mutableStateOf<String?>(null) }
    var digestLoading by remember { mutableStateOf(false) }
    var digestEntities by remember { mutableStateOf<List<DetectedEntity>>(emptyList()) }

    fun catchUp() {
        val lines = notifications.take(15).mapNotNull { notification ->
            val title = notification.title?.trim().orEmpty()
            val text = notification.text?.trim().orEmpty()
            val line = listOf(title, text)
                .filter { it.isNotBlank() }
                .joinToString(" — ")
            if (line.isBlank()) null else "${notification.appName}: $line"
        }
        if (lines.isEmpty() || digestLoading) return
        digestLoading = true
        scope.launch {
            val result = withContext(Dispatchers.Default) {
                app.aiRouter.complete(AiTask.Summarize(lines))
            }
            digest = result.text
            digestEngine = result.engineLabel
            digestLoading = false
            digestEntities = withContext(Dispatchers.Default) {
                extractEntities(context, lines.joinToString("\n"))
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BoxHeader("Inbox")
        if (!listenerEnabled) {
            NotificationAccessCard(modifier = Modifier.padding(16.dp))
        } else {
            if (notifications.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Catch me up",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            if (digestLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                )
                            } else {
                                Button(onClick = { catchUp() }) {
                                    Text("Catch me up")
                                }
                            }
                        }
                        digest?.let { text ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            digestEngine?.let { engine ->
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "AI: $engine",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (digestEntities.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                EntityChips(digestEntities)
                            }
                        }
                    }
                }
            }
            NotificationList(
                notifications = notifications,
                icons = icons,
                emptyText = "No notifications yet — new ones will land here as they arrive.",
                modifier = Modifier.weight(1f),
            )
        }
    }
}
