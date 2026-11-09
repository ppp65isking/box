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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
 * download, the per-user cloud keys, and the Nano check.
 *
 * Nothing here is a dead button: a leg with no key or no model simply
 * isn't in the chain until it is set up, and forcing a leg still
 * falls back to Smart Reply, then a plain list.
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
    var keysVersion by remember { mutableStateOf(0) }

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
                    "Reply, then a plain list — never a dead button.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            ModeRow(
                title = "Auto",
                subtitle = "Nano → on-device model → Groq → Gemini → " +
                    "OpenRouter → Smart Reply",
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
                title = "Groq cloud",
                subtitle = "Free, biggest quota — needs your key below",
                selected = mode == AiMode.GROQ,
                onClick = {
                    app.aiRouter.prefs.mode = AiMode.GROQ
                    refreshStatuses()
                },
            )
            ModeRow(
                title = "Gemini cloud",
                subtitle = "Free — needs your key below",
                selected = mode == AiMode.GEMINI_CLOUD,
                onClick = {
                    app.aiRouter.prefs.mode = AiMode.GEMINI_CLOUD
                    refreshStatuses()
                },
            )
            ModeRow(
                title = "OpenRouter free",
                subtitle = "Spare leg — 50 free requests a day, lineup rotates",
                selected = mode == AiMode.OPENROUTER,
                onClick = {
                    app.aiRouter.prefs.mode = AiMode.OPENROUTER
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

            Spacer(modifier = Modifier.height(16.dp))
            SectionTitle("Cloud keys (your own, free)")
            Text(
                text = "Groq, Gemini and OpenRouter each need your own " +
                    "free key, pasted once. Keys stay on this phone and " +
                    "are only sent to that provider. No key = that leg " +
                    "stays off. Get a key at console.groq.com, " +
                    "aistudio.google.com, or openrouter.ai.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            KeyField(
                label = "Groq key",
                saved = app.aiKeyStore.groqKey,
                key = keysVersion,
                onSave = {
                    app.aiKeyStore.groqKey = it
                    keysVersion++
                    refreshStatuses()
                },
                onClear = {
                    app.aiKeyStore.groqKey = null
                    keysVersion++
                    refreshStatuses()
                },
            )
            Spacer(modifier = Modifier.height(8.dp))
            KeyField(
                label = "Gemini key",
                saved = app.aiKeyStore.geminiKey,
                key = keysVersion,
                onSave = {
                    app.aiKeyStore.geminiKey = it
                    keysVersion++
                    refreshStatuses()
                },
                onClear = {
                    app.aiKeyStore.geminiKey = null
                    keysVersion++
                    refreshStatuses()
                },
            )
            Spacer(modifier = Modifier.height(8.dp))
            KeyField(
                label = "OpenRouter key",
                saved = app.aiKeyStore.openRouterKey,
                key = keysVersion,
                onSave = {
                    app.aiKeyStore.openRouterKey = it
                    keysVersion++
                    refreshStatuses()
                },
                onClear = {
                    app.aiKeyStore.openRouterKey = null
                    keysVersion++
                    refreshStatuses()
                },
            )

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

@Composable
private fun KeyField(
    label: String,
    saved: String?,
    key: Int,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
) {
    var value by remember(key) { mutableStateOf(saved ?: "") }
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            label = { Text(if (saved != null) "$label (saved)" else label) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row {
            Button(
                onClick = { onSave(value.trim()) },
                enabled = value.isNotBlank(),
            ) {
                Text("Save")
            }
            if (saved != null) {
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(onClick = onClear) {
                    Text("Clear")
                }
            }
        }
    }
}
