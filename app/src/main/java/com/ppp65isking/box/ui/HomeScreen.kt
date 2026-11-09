package com.ppp65isking.box.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import com.ppp65isking.box.BoxApp
import com.ppp65isking.box.data.AppCatalog
import com.ppp65isking.box.data.AppStatus
import com.ppp65isking.box.data.HealthConnectHelper
import com.ppp65isking.box.data.LaunchHistory
import com.ppp65isking.box.data.ResolvedApp
import com.ppp65isking.box.data.WeatherClient
import com.ppp65isking.box.data.loadInstalledApps
import com.ppp65isking.box.data.openApp
import com.ppp65isking.box.data.openPlayStore
import com.ppp65isking.box.data.resolveEntry
import com.ppp65isking.box.model.Category
import com.ppp65isking.box.model.WeatherNow
import com.ppp65isking.box.ui.components.AppIcon
import com.ppp65isking.box.ui.components.BoxHeader
import com.ppp65isking.box.ui.components.OnResume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface WeatherState {
    data object Loading : WeatherState
    data class Ok(val weather: WeatherNow) : WeatherState
    data class Error(val message: String) : WeatherState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as BoxApp
    val scope = rememberCoroutineScope()

    // Installed apps, loaded once (IO) and matched against the catalog.
    var installedByPackage by remember { mutableStateOf<Map<String, com.ppp65isking.box.data.InstalledApp>>(emptyMap()) }
    var installedByLabel by remember { mutableStateOf<Map<String, com.ppp65isking.box.data.InstalledApp>>(emptyMap()) }
    var installedLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val installed = loadInstalledApps(context)
            installedByPackage = installed.associateBy { it.packageName }
            installedByLabel = installed.associateBy { it.label.lowercase() }
            installedLoaded = true
        }
    }

    val resolved = remember(installedLoaded, installedByPackage, installedByLabel) {
        AppCatalog.apps.map { resolveEntry(it, installedByPackage, installedByLabel) }
    }
    val installedCount = remember(resolved) {
        resolved.count { it.status == AppStatus.INSTALLED }
    }

    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<Category?>(null) }

    val filtered = remember(resolved, query, category) {
        resolved.filter { item ->
            (category == null || item.entry.category == category) &&
                (query.isBlank() || item.entry.name.contains(query, ignoreCase = true))
        }
    }

    // Weather (Open-Meteo, no key).
    var weatherState by remember { mutableStateOf<WeatherState>(WeatherState.Loading) }
    LaunchedEffect(Unit) {
        weatherState = withContext(Dispatchers.IO) {
            when (val result = app.weatherClient.current()) {
                is WeatherClient.Result.Ok -> WeatherState.Ok(result.weather)
                is WeatherClient.Result.Error -> WeatherState.Error(result.message)
            }
        }
    }

    // Steps (Health Connect, read-only).
    var stepsState by remember { mutableStateOf<HealthConnectHelper.StepsResult?>(null) }
    var stepsReload by remember { mutableIntStateOf(0) }
    val healthPermissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { stepsReload++ }
    LaunchedEffect(stepsReload) {
        stepsState = withContext(Dispatchers.IO) { app.healthConnectHelper.todaySteps() }
    }
    // Re-check when coming back from Health Connect settings, where the
    // permission can be granted by hand.
    OnResume { stepsReload++ }

    // "Jump back in" — recently launched from Box.
    val launchHistory = remember { LaunchHistory(context) }
    var recentPackages by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(installedLoaded) {
        recentPackages = launchHistory.recent()
    }
    fun launch(item: ResolvedApp) {
        item.packageName?.let { pkg ->
            if (openApp(context, pkg)) {
                launchHistory.record(pkg)
                recentPackages = launchHistory.recent()
            }
        } ?: openPlayStore(context, item.entry)
    }

    val recentResolved = remember(recentPackages, installedByPackage) {
        recentPackages.mapNotNull { installedByPackage[it] }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BoxHeader("Box")
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            // Weather card.
            item(span = { GridItemSpan(4) }) {
                WeatherCard(
                    state = weatherState,
                    onRetry = {
                        scope.launch {
                            weatherState = WeatherState.Loading
                            weatherState = withContext(Dispatchers.IO) {
                                when (val result = app.weatherClient.current()) {
                                    is WeatherClient.Result.Ok ->
                                        WeatherState.Ok(result.weather)
                                    is WeatherClient.Result.Error ->
                                        WeatherState.Error(result.message)
                                }
                            }
                        }
                    },
                )
            }
            // Steps card.
            item(span = { GridItemSpan(4) }) {
                StepsCard(
                    state = stepsState,
                    onRequestPermission = {
                        healthPermissionLauncher.launch(
                            setOf(HealthPermission.getReadPermission(StepsRecord::class)),
                        )
                    },
                    onOpenHealthConnect = { app.healthConnectHelper.openSettings() },
                    onRetry = { stepsReload++ },
                )
            }
            // Jump back in.
            if (recentResolved.isNotEmpty()) {
                item(span = { GridItemSpan(4) }) {
                    Column {
                        Text(
                            text = "Jump back in",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(recentResolved, key = { it.packageName }) { installed ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .width(72.dp)
                                        .clickable {
                                            if (openApp(context, installed.packageName)) {
                                                launchHistory.record(installed.packageName)
                                                recentPackages = launchHistory.recent()
                                            }
                                        },
                                ) {
                                    AppIcon(icon = installed.icon, name = installed.label)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = installed.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            // Search + category chips.
            item(span = { GridItemSpan(4) }) {
                Column {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search your apps") },
                        leadingIcon = {
                            Icon(Icons.Filled.Search, contentDescription = null)
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = category == null,
                                onClick = { category = null },
                                label = { Text("All ($installedCount installed)") },
                            )
                        }
                        items(Category.entries) { cat ->
                            val count = resolved.count { it.entry.category == cat }
                            if (count > 0) {
                                FilterChip(
                                    selected = category == cat,
                                    onClick = { category = if (category == cat) null else cat },
                                    label = { Text(cat.label) },
                                )
                            }
                        }
                    }
                }
            }
            // App grid.
            if (!installedLoaded) {
                item(span = { GridItemSpan(4) }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (filtered.isEmpty()) {
                item(span = { GridItemSpan(4) }) {
                    Text(
                        text = "No apps match that search.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                items(filtered, key = { it.entry.name }) { item ->
                    AppGridCell(item = item, onClick = { launch(item) })
                }
            }
        }
    }
}

private val Category.label: String
    get() = when (this) {
        Category.FOOD -> "Food"
        Category.CHAT -> "Chat"
        Category.MUSIC -> "Music"
        Category.VIDEO -> "Video"
        Category.SOCIAL -> "Social"
        Category.EMAIL -> "Email"
        Category.FILES -> "Files"
        Category.SCHOOL -> "School"
        Category.MONEY -> "Money"
        Category.GAMES -> "Games"
        Category.AI -> "AI"
        Category.HEALTH -> "Health"
        Category.SYSTEM -> "System"
        Category.OTHER -> "More"
    }

@Composable
private fun WeatherCard(state: WeatherState, onRetry: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (state) {
                is WeatherState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Checking the weather in Woburn…")
                }
                is WeatherState.Ok -> {
                    val weather = state.weather
                    Icon(
                        imageVector = if (weather.code == 0 || weather.code == 1) {
                            Icons.Filled.WbSunny
                        } else {
                            Icons.Filled.Cloud
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "${weather.tempF}°F in Woburn",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "${weather.label} · ${weather.humidity}% humidity · " +
                                "${weather.windKph.toInt()} km/h wind",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                is WeatherState.Error -> {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Weather didn't load",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(onClick = onRetry) { Text("Retry") }
                }
            }
        }
    }
}

@Composable
private fun StepsCard(
    state: HealthConnectHelper.StepsResult?,
    onRequestPermission: () -> Unit,
    onOpenHealthConnect: () -> Unit,
    onRetry: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.DirectionsWalk,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(40.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            when (state) {
                null -> {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Checking today's steps…")
                }
                is HealthConnectHelper.StepsResult.Ok -> {
                    Column {
                        Text(
                            text = "${state.steps} steps today",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "From Health Connect",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                is HealthConnectHelper.StepsResult.NeedsPermission -> {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "See your steps here",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Allow Box to read steps from Health Connect. " +
                                "Read-only — Box never writes health data.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Button(onClick = onRequestPermission) { Text("Allow") }
                        TextButton(onClick = onOpenHealthConnect) {
                            Text("Health Connect settings")
                        }
                    }
                }
                is HealthConnectHelper.StepsResult.Unavailable -> {
                    Column {
                        Text(
                            text = "Steps aren't available",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Health Connect isn't set up on this phone.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                is HealthConnectHelper.StepsResult.Error -> {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Couldn't read steps right now.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    TextButton(onClick = onRetry) { Text("Retry") }
                }
            }
        }
    }
}

@Composable
private fun AppGridCell(item: ResolvedApp, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() },
    ) {
        AppIcon(icon = item.icon, name = item.entry.name)
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (item.status == AppStatus.NOT_INSTALLED) {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = "Not installed",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(modifier = Modifier.width(2.dp))
            }
            Text(
                text = item.entry.name,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}
