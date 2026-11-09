package com.ppp65isking.box.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ppp65isking.box.data.OAuthService

/**
 * The sign-in header shared by the Google / Spotify / Discord screens.
 *
 * Three states, all honest:
 * - Not available yet: the app's client ID for this service has not
 *   been added to the app. No Connect button — there is nothing to
 *   connect with yet.
 * - Signed out: a Connect button. Tapping it signs the USER in with
 *   their own account, in a Custom Tab.
 * - Signed in: the account label plus a Sign out button.
 */
@Composable
fun OAuthStatusCard(
    service: OAuthService,
    configured: Boolean,
    signedIn: Boolean,
    accountLabel: String?,
    onConnect: () -> Unit,
    onSignOut: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (!configured) {
                Text(
                    text = "${service.label} sign-in isn't available yet.",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            } else if (!signedIn) {
                Text(
                    text = "Connect your ${service.label} account",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Sign in with your own ${service.label} account.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onConnect, modifier = Modifier.fillMaxWidth()) {
                    Text("Connect ${service.label}")
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Signed in",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = accountLabel ?: service.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(onClick = onSignOut) {
                        Text("Sign out")
                    }
                }
            }
        }
    }
}
