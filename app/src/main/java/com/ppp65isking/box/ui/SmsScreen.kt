package com.ppp65isking.box.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ppp65isking.box.BoxApp
import com.ppp65isking.box.model.SmsConversation
import com.ppp65isking.box.model.SmsMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed interface SmsState {
    data object Loading : SmsState
    data class Conversations(val items: List<SmsConversation>) : SmsState
    data class Thread(
        val conversation: SmsConversation,
        val messages: List<SmsMessage>,
    ) : SmsState
}

private fun hasSmsPermission(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_SMS,
    ) == PackageManager.PERMISSION_GRANTED

/** SMS, read-only v1: conversations + messages. No sending.
 *  Tap a conversation to open the thread. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as BoxApp
    val scope = rememberCoroutineScope()

    var granted by remember { mutableStateOf(hasSmsPermission(context)) }
    var state by remember { mutableStateOf<SmsState>(SmsState.Loading) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        granted = isGranted
    }

    suspend fun loadConversations() {
        state = SmsState.Loading
        state = withContext(Dispatchers.IO) {
            SmsState.Conversations(app.smsRepository.conversations())
        }
    }

    suspend fun loadThread(conversation: SmsConversation) {
        state = SmsState.Loading
        state = withContext(Dispatchers.IO) {
            SmsState.Thread(
                conversation,
                app.smsRepository.messages(conversation.threadId),
            )
        }
    }

    LaunchedEffect(granted) {
        if (granted) loadConversations()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BoxHeader("Messages")
        if (!granted) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "See your texts here",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Box can show your SMS conversations and messages. " +
                        "Read-only — it can't send anything.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        permissionLauncher.launch(Manifest.permission.READ_SMS)
                    },
                ) {
                    Text("Allow SMS access")
                }
            }
            return@Column
        }
        when (val current = state) {
            is SmsState.Loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                }
            }
            is SmsState.Conversations -> {
                if (current.items.isEmpty()) {
                    Text(
                        text = "No text conversations found.",
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    LazyColumn {
                        items(current.items, key = { it.threadId }) { conversation ->
                            ConversationRow(
                                conversation = conversation,
                                onClick = { scope.launch { loadThread(conversation) } },
                            )
                        }
                    }
                }
            }
            is SmsState.Thread -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(onClick = { scope.launch { loadConversations() } }) {
                            Text("‹ All messages")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = current.conversation.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(current.messages, key = { it.id }) { message ->
                            MessageBubble(message = message)
                        }
                    }
                    Text(
                        text = "Read-only in v1 — sending comes later.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(conversation: SmsConversation, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = conversation.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatTime(conversation.date),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = conversation.snippet,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MessageBubble(message: SmsMessage) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isOutgoing) {
            Alignment.End
        } else {
            Alignment.Start
        },
    ) {
        val bubbleColor = if (message.isOutgoing) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .background(
                    color = bubbleColor,
                    shape = RoundedCornerShape(16.dp),
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = message.body,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Text(
            text = formatTime(message.date),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

private fun formatTime(epochMillis: Long): String {
    val now = System.currentTimeMillis()
    val sameDay = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        .format(Date(now)) == SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        .format(Date(epochMillis))
    return if (sameDay) {
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(epochMillis))
    } else {
        SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(epochMillis))
    }
}
