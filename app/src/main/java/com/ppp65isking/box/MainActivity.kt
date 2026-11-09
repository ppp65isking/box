package com.ppp65isking.box

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ppp65isking.box.ui.DealsScreen
import com.ppp65isking.box.ui.DiscordScreen
import com.ppp65isking.box.ui.GitHubScreen
import com.ppp65isking.box.ui.GoogleScreen
import com.ppp65isking.box.ui.HomeScreen
import com.ppp65isking.box.ui.InboxScreen
import com.ppp65isking.box.ui.MoreScreen
import com.ppp65isking.box.ui.MusicScreen
import com.ppp65isking.box.ui.RedditScreen
import com.ppp65isking.box.ui.SetupScreen
import com.ppp65isking.box.ui.SmsScreen
import com.ppp65isking.box.ui.SpotifyScreen
import com.ppp65isking.box.ui.theme.BoxTheme

private enum class Destination(
    val label: String,
    val icon: ImageVector,
) {
    HOME("Home", Icons.Filled.Home),
    INBOX("Inbox", Icons.Filled.Mail),
    DEALS("Deals", Icons.Filled.LocalOffer),
    MUSIC("Music", Icons.Filled.MusicNote),
    MORE("More", Icons.Filled.MoreHoriz),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // OAuth redirect (box://oauth/<service>?code=…): handled when the
        // activity is created cold from the redirect, and in onNewIntent
        // when it was already alive under the browser tab.
        intent?.data?.let { (application as BoxApp).handleOAuthRedirect(it) }
        enableEdgeToEdge()
        setContent {
            BoxTheme {
                BoxNav()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { (application as BoxApp).handleOAuthRedirect(it) }
    }
}

@Composable
private fun BoxNav() {
    var destination by remember { mutableStateOf(Destination.HOME) }
    // Secondary destinations live under More (no bottom-bar slot).
    var secondary by remember { mutableStateOf<String?>(null) }

    // Android 13+: ask once at startup for Box's own notification
    // permission, so the music-playback notification can show. Everything
    // else is asked for in context, on the tab that needs it.
    val context = LocalContext.current
    val app = context.applicationContext as BoxApp
    val floatingTextBar by app.uiPrefs.floatingTextBar.collectAsState()
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* granted or not — playback still works, just silently */ }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun go(target: Destination) {
        secondary = null
        destination = target
    }

    Scaffold(
        bottomBar = {
            if (floatingTextBar) {
                FloatingTextBar(
                    selected = if (secondary == null) destination else null,
                    onSelect = { go(it) },
                )
            } else {
                NavigationBar {
                    Destination.entries.forEach { entry ->
                        NavigationBarItem(
                            selected = secondary == null && destination == entry,
                            onClick = { go(entry) },
                            icon = {
                                Icon(entry.icon, contentDescription = entry.label)
                            },
                            label = { Text(entry.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.padding(padding),
        ) {
            when (secondary) {
                "github" -> GitHubScreen()
                "reddit" -> RedditScreen()
                "sms" -> SmsScreen()
                "google" -> GoogleScreen()
                "spotify" -> SpotifyScreen()
                "discord" -> DiscordScreen()
                "setup" -> SetupScreen()
                else -> when (destination) {
                    Destination.HOME -> HomeScreen()
                    Destination.INBOX -> InboxScreen()
                    Destination.DEALS -> DealsScreen()
                    Destination.MUSIC -> MusicScreen()
                    Destination.MORE -> MoreScreen(onNavigate = { secondary = it })
                }
            }
        }
    }
}

/**
 * Floating, text-only bottom bar (More → Setup toggle). A rounded pill
 * with side and bottom margins, so it floats above the content — the
 * Scaffold's content padding already clears it, because the pill's
 * height (margins included) is the bottom bar's height. Five text-only
 * tabs, no icons; the selected tab is the primary color, bold.
 */
@Composable
private fun FloatingTextBar(
    selected: Destination?,
    onSelect: (Destination) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Destination.entries.forEach { entry ->
                val isSelected = selected == entry
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onSelect(entry) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
    }
}
