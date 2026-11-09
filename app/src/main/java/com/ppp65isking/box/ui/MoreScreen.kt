package com.ppp65isking.box.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.ppp65isking.box.ui.components.BoxHeader
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ppp65isking.box.data.openUrl

/** More: the tabs that live outside the bottom bar, plus an honest list of
 *  what's coming tomorrow (needs Anthony's dev accounts + sign-ins). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(onNavigate: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        BoxHeader("More")
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = "Browse",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            MoreRow(title = "GitHub", subtitle = "Search public repos") {
                onNavigate("github")
            }
            MoreRow(title = "Reddit", subtitle = "Browse r/popular and subreddits") {
                onNavigate("reddit")
            }
            MoreRow(title = "Messages", subtitle = "Your SMS conversations") {
                onNavigate("sms")
            }
            Text(
                text = "Coming tomorrow",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Text(
                text = "These need dev accounts and a sign-in from you — " +
                    "they're not in Box yet, on purpose:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "• Gmail — read + search your mail\n" +
                    "• Spotify — your library and playback\n" +
                    "• Discord and Telegram — chats inside Box\n" +
                    "• Outlook — work and school mail\n" +
                    "• Threads — feed and posting\n" +
                    "• AI chats — Gemini, ChatGPT, Grok, Copilot, Perplexity",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            val context = LocalContext.current
            Text(
                text = "About Box",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Text(
                text = "Box 1.0 — one hub for your apps. Built with Material 3. " +
                    "Music plays through the same public YouTube Music endpoints as the " +
                    "TV app. Nothing in Box sends your data anywhere except the services " +
                    "you ask it to reach.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MoreRow(
                title = "Source code",
                subtitle = "github.com/ppp65isking/box",
            ) {
                openUrl(context, "https://github.com/ppp65isking/box")
            }
        }
    }
}

@Composable
private fun MoreRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
