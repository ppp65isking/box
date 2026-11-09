package com.ppp65isking.box.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import com.ppp65isking.box.ui.components.BoxHeader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ppp65isking.box.BoxApp
import com.ppp65isking.box.data.OAuthService
import com.ppp65isking.box.data.openUrl

/**
 * Developer setup — Box's OWN OAuth client IDs, one per service.
 *
 * These IDs belong to the app, not to any user. Anthony registers them
 * once as the developer (the steps are in SETUP.md at the repo root);
 * every user then just taps Connect on the service's screen and signs
 * in with their own account. Nobody using Box ever needs a dev account
 * or a client ID — if a field here is empty, the service's screen says
 * so and hides its Connect button.
 *
 * No client secrets are asked for anywhere: all three services use
 * PKCE, so the client ID alone is the credential.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as BoxApp

    Column(modifier = Modifier.fillMaxSize()) {
        BoxHeader("Setup")
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "These are Box's own app IDs — one per service, " +
                    "entered once by the developer. Users never touch this " +
                    "page: they just tap Connect and sign in with their own " +
                    "account. Full steps are in SETUP.md in the repo.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            ClientIdSection(
                service = OAuthService.GOOGLE,
                where = "Google Cloud Console → APIs & Services → Credentials → " +
                    "Create Credentials → OAuth client ID → Desktop app.",
                redirectUri = "box://oauth/google",
                consoleUrl = "https://console.cloud.google.com/apis/credentials",
            )
            ClientIdSection(
                service = OAuthService.SPOTIFY,
                where = "Spotify Developer Dashboard → Create app.",
                redirectUri = "box://oauth/spotify",
                consoleUrl = "https://developer.spotify.com/dashboard",
            )
            ClientIdSection(
                service = OAuthService.DISCORD,
                where = "Discord Developer Portal → New Application → OAuth2.",
                redirectUri = "box://oauth/discord",
                consoleUrl = "https://discord.com/developers/applications",
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Reddit search has its own field on the More page " +
                    "(same idea: one app ID for Box, not per user).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ClientIdSection(
    service: OAuthService,
    where: String,
    redirectUri: String,
    consoleUrl: String,
) {
    val context = LocalContext.current
    val app = context.applicationContext as BoxApp
    var clientId by remember { mutableStateOf(app.oauthStore.clientId(service).orEmpty()) }
    var saved by remember { mutableStateOf(false) }
    val configured = app.oauthStore.isConfigured(service)

    Text(
        text = service.label,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
    Text(
        text = "Create the app here: $where",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = "Register this exact redirect URI: $redirectUri",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = if (configured) "Status: configured ✓" else "Status: not set up yet",
        style = MaterialTheme.typography.bodyMedium,
        color = if (configured) {
            MaterialTheme.colorScheme.secondary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = clientId,
        onValueChange = { clientId = it.trim(); saved = false },
        placeholder = { Text("${service.label} client ID") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(8.dp))
    Button(
        onClick = {
            app.oauthStore.setClientId(service, clientId.ifBlank { null })
            app.bumpOAuthVersion()
            saved = true
        },
        enabled = clientId.isNotBlank(),
    ) {
        Text(if (saved) "Saved" else "Save ${service.label} client ID")
    }
    Spacer(modifier = Modifier.height(4.dp))
    Button(onClick = { openUrl(context, consoleUrl) }) {
        Text("Open ${service.label} console")
    }
    Spacer(modifier = Modifier.height(16.dp))
}
