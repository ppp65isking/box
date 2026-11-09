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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

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
    val floatingBar by app.uiPrefs.floatingBar.collectAsState()
    // One Haze state for the whole nav: every screen registers as a
    // blur source, and the floating bar blurs whatever scrolls under it.
    val hazeState = rememberHazeState()
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
        // The floating bar is NOT a Scaffold bottom bar: it overlays the
        // content (see below) so lists scroll underneath it and the blur
        // has something to blur. Only the standard bar takes the slot.
        bottomBar = {
            if (!floatingBar) {
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // The screens are the blur source. They fill the whole area,
            // so content scrolls under the floating pill.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState),
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
            if (floatingBar) {
                FloatingIconBar(
                    selected = if (secondary == null) destination else null,
                    onSelect = { go(it) },
                    hazeState = hazeState,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

/**
 * Floating, icon-only bottom bar (Settings toggle, default on). A
 * detached pill with side and bottom margins and a shadow, so it reads
 * as floating above the content — and the content scrolling under it is
 * really blurred (Haze), not just covered by a flat color. Five icons,
 * no labels; the selected tab is the primary color.
 */
@Composable
private fun FloatingIconBar(
    selected: Destination?,
    onSelect: (Destination) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val pillShape = RoundedCornerShape(28.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            .shadow(elevation = 12.dp, shape = pillShape)
            .clip(pillShape)
            .hazeEffect(state = hazeState) {
                blurRadius = 20.dp
                // Frosted tint over the blur, so the pill reads on any
                // background and the icons stay legible.
                tint = HazeTint(
                    MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
                )
            }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Destination.entries.forEach { entry ->
            val isSelected = selected == entry
            IconButton(onClick = { onSelect(entry) }) {
                Icon(
                    imageVector = entry.icon,
                    contentDescription = entry.label,
                    tint = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}
