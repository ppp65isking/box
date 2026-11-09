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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import com.ppp65isking.box.ui.components.BoxHeader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ppp65isking.box.BoxApp
import com.ppp65isking.box.data.RedditClient
import com.ppp65isking.box.data.openUrl
import com.ppp65isking.box.data.redditConfiguredClientId
import com.ppp65isking.box.model.RedditPost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface RedditState {
    data object Loading : RedditState
    data class Ok(val posts: List<RedditPost>) : RedditState
    data class Error(val message: String) : RedditState
}

private enum class RedditMode(val label: String) {
    POPULAR("Popular"),
    SEARCH("Search"),
    SUBREDDIT("Subreddit"),
}

/** Public Reddit browsing — read-only, no account, no auth. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RedditScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as BoxApp
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(RedditMode.POPULAR) }
    var query by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<RedditState>(RedditState.Loading) }

    fun load() {
        state = RedditState.Loading
        scope.launch {
            state = withContext(Dispatchers.IO) {
                val result = when (mode) {
                    RedditMode.POPULAR -> app.redditClient.popular()
                    RedditMode.SEARCH -> app.redditClient.search(query)
                    RedditMode.SUBREDDIT -> app.redditClient.subreddit(query)
                }
                when (result) {
                    is RedditClient.Result.Ok -> RedditState.Ok(result.posts)
                    is RedditClient.Result.Error -> RedditState.Error(result.message)
                }
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    Column(modifier = Modifier.fillMaxSize()) {
        BoxHeader("Reddit")
        // Search runs on Box's own Reddit app ID (shipped in the app).
        // Browsing r/popular and subreddits works without it; search
        // says plainly that it isn't available yet until the ID is in.
        if (redditConfiguredClientId() == null) {
            Text(
                text = "Reddit search isn't available yet. Browsing still works.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RedditMode.entries.forEach { entry ->
                FilterChip(
                    selected = mode == entry,
                    onClick = {
                        mode = entry
                        if (entry == RedditMode.POPULAR) load()
                    },
                    label = { Text(entry.label) },
                )
            }
        }
        if (mode != RedditMode.POPULAR) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = {
                        Text(
                            if (mode == RedditMode.SEARCH) {
                                "Search Reddit"
                            } else {
                                "Subreddit name (e.g. android)"
                            },
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = null)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { load() },
                    enabled = query.isNotBlank() && state != RedditState.Loading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (mode == RedditMode.SEARCH) "Search" else "Open subreddit")
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        when (val current = state) {
            is RedditState.Loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                }
            }
            is RedditState.Ok -> {
                if (current.posts.isEmpty()) {
                    Text(
                        text = "Nothing here — try another search.",
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    LazyColumn {
                        items(current.posts, key = { it.permalink }) { post ->
                            RedditPostRow(post = post)
                        }
                    }
                }
            }
            is RedditState.Error -> {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Couldn't load Reddit: ${current.message}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { load() }) { Text("Retry") }
                }
            }
        }
    }
}

@Composable
private fun RedditPostRow(post: RedditPost) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { openUrl(context, post.permalink) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (post.thumbnail != null) {
            AsyncImage(
                model = post.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = post.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "r/${post.subreddit} · u/${post.author}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${post.score} points · ${post.comments} comments",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
