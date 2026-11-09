package com.ppp65isking.box.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import com.ppp65isking.box.data.ai.AiMode
import com.ppp65isking.box.data.ai.LegStatus
import com.ppp65isking.box.data.ai.ModelState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings → "AI model". The router's mode (Auto or force one leg),
 * what each leg can do on this phone, the opt-in on-device model
 * download, and the Nano check.
 *
 * NO-SIGNUP ONLY (Anthony's rule, 2026-11-09): every leg here is
 * free and public with no account and no key to paste — Nano on the
 * phone, the downloaded Qwen model, Pollinations cloud, Smart Reply.
 * The keyed cloud legs (Groq, Gemini cloud, OpenRouter) and their
 * key entry fields were removed.
 *
 * Nothing here is a dead button: a leg with no model simply isn't in
 * the chain until it is set up, and forcing a leg still falls back to
 * Smart Reply, then a plain list.
 */
@Composable
fun AiSettingsScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as BoxApp
    val scope = rememberCoroutineScope()

    val mode by app.aiRouter.prefs.modeFlow.collectAsState()
    val lastEngine by app.aiRouter.lastEngine.collectAsState()
    val modelState by app.liteRtModelManager.state.collectAsState()

    var statuses by remember { mutableStateOf<List<LegStatus>>(emptyList()) }
    var nanoCheck by remember { mutableStateOf<String?>(null) }
    var nanoChecking by remember { mutableStateOf(false) }

    fun refreshStatuses() {
        scope.launch {
            statuses = withContext(Dispatchers.Default) {
                app.aiRouter.legStatus()
            }
        }
    }

    LaunchedEffect(Unit) { refreshStatuses() }

    Column(modifier = Modifier.fillMaxSize()) {
        BoxHeader("AI model")
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionTitle("How Box answers")
            Text(
                text = "Auto tries each model in order and switches when one " +
                    "isn't available. Forcing one still falls back to Smart " +
                    "Reply, then a plain list — never a dead button. " +
                    "Every model here is free with no sign-up and no key " +
                    "to paste.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            ModeRow(
                title = "Auto",
                subtitle = "Nano → on-device model → Pollinations → Smart Reply",
                selected = mode == AiMode.AUTO,
                onClick = {
                    app.aiRouter.prefs.mode = AiMode.AUTO
                    refreshStatuses()
                },
            )
            ModeRow(
                title = "Gemini Nano",
                subtitle = "On-device, instant, private — newer Pixels and flagships",
                selected = mode == AiMode.NANO,
                onClick = {
                    app.aiRouter.prefs.mode = AiMode.NANO
                    refreshStatuses()
                },
            )
            ModeRow(
                title = "On-device model (Qwen 1.5B)",
                subtitle = "Downloaded once, runs offline on any phone",
                selected = mode == AiMode.LITERT,
                onClick = {
                    app.aiRouter.prefs.mode = AiMode.LITERT
                    refreshStatuses()
                },
            )
            ModeRow(
                title = "Pollinations cloud",
                subtitle = "Free, no sign-up — 1 request per 45 seconds",
                selected = mode == AiMode.POLLINATIONS,
                onClick = {
                    app.aiRouter.prefs.mode = AiMode.POLLINATIONS
                    refreshStatuses()
                },
            )
            ModeRow(
                title = "Smart Reply only",
                subtitle = "ML Kit classic — works on every phone, no model",
                selected = mode == AiMode.CLASSIC,
                onClick = {
                    app.aiRouter.prefs.mode = AiMode.CLASSIC
                    refreshStatuses()
                },
            )

            Spacer(modifier = Modifier.height(16.dp))
            SectionTitle("What's available")
            if (statuses.isEmpty()) {
                Text(
                    text = "Checking…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                statuses.forEach { status ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = status.label,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = status.detail,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = if (status.ready) "Ready" else "Off",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (status.ready) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            SectionTitle("Gemini Nano")
            Text(
                text = "Nano only runs while Box is open, on phones that " +
                    "have it. This check tells you if sideloaded Box can " +
                    "reach it on this phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    nanoChecking = true
                    scope.launch {
                        nanoCheck = app.aiRouter.nanoEngine.statusText()
                        nanoChecking = false
                        refreshStatuses()
                    }
                },
                enabled = !nanoChecking,
            ) {
                Text("Check Nano on this phone")
            }
            if (nanoChecking) {
                Spacer(modifier = Modifier.height(8.dp))
                CircularProgressIndicator()
            }
            nanoCheck?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            SectionTitle("On-device model")
            Text(
                text = "Qwen 2.5 1.5B — runs offline on any phone with " +
                    "enough RAM. Downloaded once over Wi-Fi (~1.6 GB) " +
                    "and stored on this phone. The download is checked " +
                    "against its exact size and fingerprint.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            when (val current = modelState) {
                is ModelState.NotDownloaded -> {
                    Button(
                        onClick = {
                            scope.launch { app.liteRtModelManager.download() }
                        },
                    ) {
                        Text("Download on-device model (~1.6 GB, Wi-Fi only)")
                    }
                }
                is ModelState.Downloading -> {
                    val progress = if (current.totalBytes > 0) {
                        current.downloadedBytes.toFloat() /
                            current.totalBytes.toFloat()
                    } else {
                        0f
                    }
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Downloading… " +
                            "${current.downloadedBytes / 1_000_000} / " +
                            "${current.totalBytes / 1_000_000} MB",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                is ModelState.Ready -> {
                    Text(
                        text = "Downloaded — ready.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { app.liteRtModelManager.delete() },
                    ) {
                        Text("Delete model")
                    }
                }
                is ModelState.Failed -> {
                    Text(
                        text = current.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            scope.launch { app.liteRtModelManager.download() }
                        },
                    ) {
                        Text("Try again")
                    }
                }
            }

            lastEngine?.let {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Last answer came from: $it",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(72.dp))
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun ModeRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.width(8.dp))
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
    }
}
