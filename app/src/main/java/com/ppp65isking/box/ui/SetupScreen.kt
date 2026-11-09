package com.ppp65isking.box.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import com.ppp65isking.box.ui.components.BoxHeader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ppp65isking.box.BoxApp
import com.ppp65isking.box.data.UsageStatsHelper
import com.ppp65isking.box.data.openUrl
import com.ppp65isking.box.ui.components.OnResume

/**
 * Settings, in sections — Appearance, Home, Privacy & permissions,
 * AI, About — instead of one flat list. Same toggles as before,
 * same behavior; only the layout changed. Material 3 Expressive
 * styling: rounder section cards, the theme's expressive motion.
 *
 * Nothing about accounts lives here. Each service's Connect button
 * is on that service's own screen, and the app's client IDs ship in
 * the app (see SETUP.md), so there is nothing to paste here.
 */
@Composable
fun SetupScreen(onNavigate: (String) -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as BoxApp
    val floatingBar by app.uiPrefs.floatingBar.collectAsState()
    val healthConnect by app.uiPrefs.healthConnect.collectAsState()
    val railOnLeft by app.uiPrefs.railOnLeft.collectAsState()
    val previewRail by app.uiPrefs.previewRail.collectAsState()
    val mostUsedFirst by app.uiPrefs.mostUsedFirst.collectAsState()

    // Usage-access grant, re-checked on resume — it can appear while
    // Box is in Settings.
    var usageGranted by remember { mutableStateOf(false) }
    fun refreshUsage() {
        usageGranted = UsageStatsHelper.isGranted(context)
    }
    OnResume { refreshUsage() }
    LaunchedEffect(Unit) { refreshUsage() }

    Column(modifier = Modifier.fillMaxSize()) {
        BoxHeader("Settings")
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsSection("Appearance") {
                SwitchRow(
                    title = "Floating bottom bar",
                    subtitle = "Icons in a frosted floating pill",
                    checked = floatingBar,
                    onCheckedChange = { app.uiPrefs.setFloatingBar(it) },
                )
                HorizontalDivider()
                SwitchRow(
                    title = "Foldable rail on the left",
                    subtitle = "On foldables the icon rail sits at the bottom " +
                        "right — flip it to the left.",
                    checked = railOnLeft,
                    onCheckedChange = { app.uiPrefs.setRailOnLeft(it) },
                )
                HorizontalDivider()
                SwitchRow(
                    title = "Preview foldable layout",
                    subtitle = "Show the foldable icon rail on this phone.",
                    checked = previewRail,
                    onCheckedChange = { app.uiPrefs.setPreviewRail(it) },
                )
            }

            SettingsSection("Home") {
                SwitchRow(
                    title = "Show most-used first",
                    subtitle = "Order the Home grid by how recently you " +
                        "used each app, and fill \"Jump back in\" from it.",
                    checked = mostUsedFirst,
                    onCheckedChange = { app.uiPrefs.setMostUsedFirst(it) },
                )
                if (mostUsedFirst) {
                    HorizontalDivider()
                    if (usageGranted) {
                        Text(
                            text = "Usage access is on — Home orders apps by " +
                                "how recently you used them.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    } else {
                        Column(modifier = Modifier.padding(vertical = 12.dp)) {
                            Text(
                                text = "Box also needs usage access to see " +
                                    "which apps you use. It only sees app " +
                                    "usage times — nothing inside apps.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { UsageStatsHelper.openSettings(context) },
                            ) {
                                Text("Open usage access settings")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Can't find Box in the list? Check " +
                                    "App info → ⋮ → Allow restricted settings, " +
                                    "then come back here.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            SettingsSection("Privacy & permissions") {
                SwitchRow(
                    title = "Health Connect",
                    subtitle = "Show your steps on Home. Off means Box never " +
                        "reads Health Connect.",
                    checked = healthConnect,
                    onCheckedChange = { app.uiPrefs.setHealthConnect(it) },
                )
            }

            SettingsSection("AI") {
                NavRow(
                    title = "AI model",
                    subtitle = "Multiple free models, automatic fallback — " +
                        "no sign-up, no key to paste",
                    onClick = { onNavigate("ai") },
                )
            }

            SettingsSection("About") {
                Text(
                    text = "Box 1.0 — one hub for your apps. Nothing in Box " +
                        "sends your data anywhere except the services you " +
                        "ask it to reach.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                HorizontalDivider()
                NavRow(
                    title = "Source code",
                    subtitle = "github.com/ppp65isking/box",
                    onClick = {
                        openUrl(context, "https://github.com/ppp65isking/box")
                    },
                )
            }
            Spacer(modifier = Modifier.height(72.dp))
        }
    }
}

/** One settings section: a small colored header over a rounder
 *  expressive card holding the rows. */
@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
    )
    Card(
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            content = content,
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
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
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun NavRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
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
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}
