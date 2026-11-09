package com.ppp65isking.box.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import com.ppp65isking.box.ui.components.BoxHeader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ppp65isking.box.BoxApp
import com.ppp65isking.box.data.GitHubClient
import com.ppp65isking.box.data.openUrl
import com.ppp65isking.box.model.GitHubRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed interface GitHubState {
    data object Idle : GitHubState
    data object Loading : GitHubState
    data class Ok(val repos: List<GitHubRepo>, val remaining: Int?) : GitHubState
    data class RateLimited(val resetsAtEpochSeconds: Long?) : GitHubState
    data class Error(val message: String) : GitHubState
}

private val SUGGESTIONS = listOf("android", "kotlin", "compose", "game", "music")

/** Public GitHub repo search — no account, no token.
 *  Unauthenticated limit is 60 requests/hour per IP. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitHubScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as BoxApp
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<GitHubState>(GitHubState.Idle) }

    fun search(term: String) {
        if (term.isBlank()) return
        query = term
        state = GitHubState.Loading
        scope.launch {
            state = withContext(Dispatchers.IO) {
                when (val result = app.gitHubClient.searchRepos(term)) {
                    is GitHubClient.Result.Ok ->
                        GitHubState.Ok(result.repos, result.remaining)
                    is GitHubClient.Result.RateLimited ->
                        GitHubState.RateLimited(result.resetsAtEpochSeconds)
                    is GitHubClient.Result.Error ->
                        GitHubState.Error(result.message)
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BoxHeader("GitHub")
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search public repos") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { search(query) },
                enabled = query.isNotBlank() && state != GitHubState.Loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Search")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SUGGESTIONS.forEach { suggestion ->
                    Button(onClick = { search(suggestion) }) {
                        Text(suggestion)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        when (val current = state) {
            is GitHubState.Idle -> {
                Text(
                    text = "Search GitHub's public repos — no sign-in needed. " +
                        "Tap a repo to open it.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            is GitHubState.Loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                }
            }
            is GitHubState.Ok -> {
                if (current.repos.isEmpty()) {
                    Text(
                        text = "No repos matched that search.",
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    LazyColumn {
                        items(current.repos, key = { it.fullName }) { repo ->
                            GitHubRepoRow(repo = repo)
                        }
                    }
                }
            }
            is GitHubState.RateLimited -> {
                val resetText = current.resetsAtEpochSeconds?.let { epoch ->
                    val time = SimpleDateFormat("h:mm a", Locale.getDefault())
                        .format(Date(epoch * 1000))
                    "Try again after $time."
                } ?: "Try again in a bit."
                Text(
                    text = "GitHub's free limit (60 searches an hour) is used up. $resetText",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp),
                )
            }
            is GitHubState.Error -> {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Couldn't search GitHub: ${current.message}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { search(query) }) { Text("Retry") }
                }
            }
        }
    }
}

@Composable
private fun GitHubRepoRow(repo: GitHubRepo) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { openUrl(context, repo.htmlUrl) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = repo.fullName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            repo.description?.takeIf { it.isNotBlank() }?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = repo.stars.toString(),
                    style = MaterialTheme.typography.labelMedium,
                )
                repo.language?.let { language ->
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = language,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
