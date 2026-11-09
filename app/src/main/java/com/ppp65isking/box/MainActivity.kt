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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
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
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import com.ppp65isking.box.ui.AiSettingsScreen
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
import com.ppp65isking.box.ui.SteamScreen
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
    val floatingBar by app.uiPrefs.floatingBar.collectAsState()
    val railOnLeft by app.uiPrefs.railOnLeft.collectAsState()
    val previewRail by app.uiPrefs.previewRail.collectAsState()

    // Haze state for the floating bar's real blur: the screen content
    // is the blur source, the bar is the effect. Only used while the
    // floating bar is showing (see below).
    val hazeState = rememberHazeState()

    // Foldables (any FoldingFeature, folded or unfolded) get the
    // vertical icon rail instead of a bottom bar — see FoldableRail.
    // Phones keep the floating pill / standard bar. The Settings
    // preview toggle forces the rail on any phone.
    val activity = context as ComponentActivity
    val layoutInfo by WindowInfoTracker.getOrCreate(activity)
        .windowLayoutInfo(activity)
        .collectAsState(initial = null)
    val isFoldable = previewRail || layoutInfo?.displayFeatures
        ?.any { it is FoldingFeature } == true
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
        // content (see below) so lists scroll underneath it. Only the
        // standard bar takes the slot — and foldables take neither,
        // they get the vertical rail instead.
        bottomBar = {
            if (!floatingBar && !isFoldable) {
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
            // The screens fill the whole area, so content scrolls under
            // the floating pill. The content is also the Haze blur
            // source: the floating bar blurs whatever scrolls under it.
            // Only registered while the floating bar is showing — the
            // standard bar and the foldable rail don't blur anything.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (floatingBar && !isFoldable) {
                            Modifier.hazeSource(state = hazeState)
                        } else {
                            Modifier
                        },
                    ),
            ) {
                when (secondary) {
                    "github" -> GitHubScreen()
                    "reddit" -> RedditScreen()
                    "sms" -> SmsScreen()
                    "google" -> GoogleScreen()
                    "spotify" -> SpotifyScreen()
                    "discord" -> DiscordScreen()
                    "steam" -> SteamScreen()
                    "ai" -> AiSettingsScreen()
                    "setup" -> SetupScreen()
                    else -> when (destination) {
                        Destination.HOME -> HomeScreen(onOpenHub = { secondary = it })
                        Destination.INBOX -> InboxScreen()
                        Destination.DEALS -> DealsScreen()
                        Destination.MUSIC -> MusicScreen()
                        Destination.MORE -> MoreScreen(onNavigate = { secondary = it })
                    }
                }
            }
            if (isFoldable) {
                FoldableRail(
                    selected = if (secondary == null) destination else null,
                    onSelect = { go(it) },
                    onLeft = railOnLeft,
                    modifier = Modifier.align(
                        if (railOnLeft) Alignment.BottomStart else Alignment.BottomEnd,
                    ),
                )
            } else if (floatingBar) {
                FloatingIconBar(
                    selected = if (secondary == null) destination else null,
                    onSelect = { go(it) },
                    modifier = Modifier.align(Alignment.BottomCenter),
                    hazeState = hazeState,
                )
            }
        }
    }
}

/**
 * Foldable nav: a vertical, icon-only rail in a frosted pill, docked
 * to the bottom edge on the right (or left, per Settings). Folded and
 * unfolded alike — the rail stays put when the device opens. Same
 * five tabs as the bottom bar, same selected color, no labels.
 * Phones never see this; only devices with a FoldingFeature do.
 */
@Composable
private fun FoldableRail(
    selected: Destination?,
    onSelect: (Destination) -> Unit,
    onLeft: Boolean,
    modifier: Modifier = Modifier,
) {
    val pillShape = RoundedCornerShape(28.dp)
    Column(
        modifier = modifier
            .wrapContentSize()
            .padding(
                start = if (onLeft) 16.dp else 0.dp,
                end = if (onLeft) 0.dp else 16.dp,
                bottom = 12.dp,
            )
            .shadow(elevation = 12.dp, shape = pillShape)
            .clip(pillShape)
            .background(
                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.7f),
                pillShape,
            )
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
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

/**
 * Floating, icon-only bottom bar (Settings toggle, default on). A
 * detached pill with side and bottom margins and a shadow, so it reads
 * as floating above the content — and the fill is a REAL backdrop blur
 * (Haze 1.7.3): the screen content behind the bar is blurred through
 * it, over the same translucent surfaceContainer tint the fake fill
 * used. Five icons, no labels; the selected tab is the primary color.
 *
 * The blur needs the screen content registered as a Haze source (see
 * BoxNav) and the bar as the effect, both sharing one HazeState.
 *
 * Haze 2.x would be nicer (hazeBlur + theme-aware styles), but it is
 * compiled with Kotlin 2.4 metadata and this project is stuck on the
 * Kotlin 2.2.10 AGP 9.1.1 bundles — see build.gradle.kts. 1.7.3 is
 * the newest Haze the toolchain can read.
 */
@Composable
private fun FloatingIconBar(
    selected: Destination?,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState,
) {
    val pillShape = RoundedCornerShape(28.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            .shadow(elevation = 12.dp, shape = pillShape)
            .clip(pillShape)
            .hazeEffect(
                state = hazeState,
                style = HazeStyle(
                    backgroundColor = MaterialTheme.colorScheme
                        .surfaceContainer.copy(alpha = 0.7f),
                    // HazeStyle has two constructors (tints list vs
                    // single tint) — passing tints pins the first so
                    // the call isn't ambiguous.
                    tints = emptyList(),
                    blurRadius = 20.dp,
                    noiseFactor = 0f,
                ),
            )
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
