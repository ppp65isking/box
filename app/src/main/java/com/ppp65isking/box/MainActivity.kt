package com.ppp65isking.box

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.ppp65isking.box.ui.DealsScreen
import com.ppp65isking.box.ui.GitHubScreen
import com.ppp65isking.box.ui.HomeScreen
import com.ppp65isking.box.ui.InboxScreen
import com.ppp65isking.box.ui.MoreScreen
import com.ppp65isking.box.ui.MusicScreen
import com.ppp65isking.box.ui.RedditScreen
import com.ppp65isking.box.ui.SmsScreen
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
        enableEdgeToEdge()
        setContent {
            BoxTheme {
                BoxNav()
            }
        }
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
        },
    ) { padding ->
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.padding(padding),
        ) {
            when (secondary) {
                "github" -> GitHubScreen()
                "reddit" -> RedditScreen()
                "sms" -> SmsScreen()
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
