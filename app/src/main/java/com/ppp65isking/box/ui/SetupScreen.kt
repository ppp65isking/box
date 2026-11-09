package com.ppp65isking.box.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import com.ppp65isking.box.ui.components.BoxHeader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ppp65isking.box.BoxApp

/**
 * Settings. Just the app's own display options — nothing about
 * accounts lives here. Each service's Connect button is on that
 * service's own screen, and the app's client IDs ship in the app
 * (see SETUP.md), so there is nothing to paste here.
 */
@Composable
fun SetupScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as BoxApp
    val floatingBar by app.uiPrefs.floatingBar.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        BoxHeader("Settings")
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Floating bottom bar",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Icons in a floating pill that blurs what's under it",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = floatingBar,
                    onCheckedChange = { app.uiPrefs.setFloatingBar(it) },
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
