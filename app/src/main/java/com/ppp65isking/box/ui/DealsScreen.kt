package com.ppp65isking.box.ui

import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import com.ppp65isking.box.ui.components.BoxHeader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ppp65isking.box.data.AppCatalog
import com.ppp65isking.box.data.NotificationRepository
import com.ppp65isking.box.data.loadInstalledApps
import com.ppp65isking.box.ui.components.NotificationAccessCard
import com.ppp65isking.box.ui.components.NotificationList
import com.ppp65isking.box.ui.components.OnResume
import com.ppp65isking.box.ui.components.isNotificationListenerEnabled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Deals and discounts, built from the food + rewards apps' notifications.
 *  Food apps have no APIs, so their deal alerts are the integration. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DealsScreen() {
    val context = LocalContext.current
    val notifications by NotificationRepository.notifications.collectAsState()

    var listenerEnabled by remember { mutableStateOf(false) }
    fun refreshListenerState() {
        listenerEnabled = isNotificationListenerEnabled(context)
    }
    OnResume { refreshListenerState() }
    LaunchedEffect(Unit) { refreshListenerState() }

    var icons by remember { mutableStateOf<Map<String, Drawable?>>(emptyMap()) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            icons = loadInstalledApps(context)
                .associate { it.packageName to it.icon }
        }
    }

    val deals = remember(notifications) {
        notifications.filter {
            AppCatalog.isDealApp(it.packageName, it.appName)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BoxHeader("Deals")
        if (!listenerEnabled) {
            NotificationAccessCard(modifier = Modifier.padding(16.dp))
        } else {
            NotificationList(
                notifications = deals,
                icons = icons,
                emptyText = "No deals right now — discount alerts from your food " +
                    "and rewards apps will land here as they arrive.",
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
