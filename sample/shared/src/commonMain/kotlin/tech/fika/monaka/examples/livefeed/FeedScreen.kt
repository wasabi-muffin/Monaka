package tech.fika.monaka.examples.livefeed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tech.fika.monaka.ext.bindLifecycle
import tech.fika.monaka.dsl.store
import tech.fika.monaka.ext.formatRelativeTime
import tech.fika.monaka.ext.handleEffects
import tech.fika.monaka.ext.rememberStore
import tech.fika.monaka.ext.render
import tech.fika.monaka.ext.toViewStore

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveFeedScreen(onBack: () -> Unit) {
    val store = rememberStore { scope ->
        store(
            stateMachine = FeedStateMachine(
                feedRepository = FakeFeedRepository(),
                analyticsRepository = FakeAnalyticsRepository(),
            ),
            scope = scope,
        )
    }
    val snackbarHostState = remember { SnackbarHostState() }
    var query by rememberSaveable { mutableStateOf("") }

    val (state, dispatch) = store
        .bindLifecycle()
        .handleEffects { effect ->
            if (effect is FeedEffect.ShowToast) {
                snackbarHostState.showSnackbar(effect.message)
            }
        }.toViewStore()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live Feed") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    state.render<FeedState.Active> {
                        TextButton(
                            onClick = {
                                if (renderState.isLive) dispatch(FeedAction.PauseLive) else dispatch(FeedAction.GoLive)
                            },
                        ) {
                            Text(if (renderState.isLive) "Pause Live" else "Go Live")
                        }
                        TextButton(onClick = { dispatch(FeedAction.Refresh) }) {
                            Text("Refresh")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    dispatch(FeedAction.QueryChanged(it))
                },
                label = { Text("Search  ·  type \"error\" to test failure") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        TextButton(
                            onClick = {
                                query = ""
                                dispatch(FeedAction.Clear)
                            }
                        ) { Text("Clear") }
                    }
                } else null,
            )
            state.render<FeedState.Idle> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Type to search", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            state.render<FeedState.Active> {
                if (renderState.isLoading && renderState.items.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    if (renderState.isLive) {
                        Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                CircularProgressIndicator(Modifier.size(10.dp), strokeWidth = 1.5.dp)
                                Text("Live", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        if (renderState.items.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(48.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("No results for \"${renderState.query}\"")
                                }
                            }
                        } else {
                            items(renderState.items, key = { it.id }) { item ->
                                FeedItemRow(item = item, onClick = {
                                    dispatch(FeedAction.ItemViewed(item.id))
                                })
                            }
                        }
                    }
                }
            }

            state.render<FeedState.Failed> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Text("Search failed", style = MaterialTheme.typography.titleMedium)
                        Text(
                            renderState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (renderState.retryCount > 0) {
                            Text(
                                "Attempt ${renderState.retryCount}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(onClick = { dispatch(FeedAction.Retry) }) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedItemRow(item: FeedItem, onClick: () -> Unit) {
    val dateStr = remember(item.timestamp) { formatRelativeTime(item.timestamp) }
    Surface(onClick = onClick) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(item.title, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(2.dp))
            Text(
                dateStr,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}
