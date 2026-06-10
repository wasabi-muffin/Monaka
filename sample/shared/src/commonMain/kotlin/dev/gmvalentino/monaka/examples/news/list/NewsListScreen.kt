package dev.gmvalentino.monaka.examples.news.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gmvalentino.monaka.ext.bindLifecycle
import dev.gmvalentino.monaka.ext.handleEffects
import dev.gmvalentino.monaka.ext.render
import dev.gmvalentino.monaka.ext.toViewStore
import dev.zacsweers.metrox.viewmodel.metroViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsListScreen(
    onNavigateToDetails: (Int) -> Unit,
    onBack: () -> Unit,
    viewModel: NewsListViewModel = metroViewModel(),
) {
    val (state, dispatch) = viewModel.store
        .bindLifecycle()
        .handleEffects { effect ->
            when (effect) {
                is NewsListEffect.NavigateNewsDetails -> onNavigateToDetails(effect.id)
            }
        }
        .toViewStore()

    LaunchedEffect(Unit) {
        dispatch(NewsListAction.LoadInitial)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("News") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            state.render<NewsListState.InitialLoading> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            state.render<NewsListState.InitialError> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("Failed to load news", style = MaterialTheme.typography.bodyLarge)
                        Button(onClick = { dispatch(NewsListAction.Error.ClickRetry) }) {
                            Text("Retry")
                        }
                    }
                }
            }

            state.render<NewsListState.Stable> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Button(
                                onClick = { dispatch(NewsListAction.ClickReadAll) },
                                enabled = renderState is NewsListState.Stable.Initial,
                            ) {
                                state.render<NewsListState.Stable.Loading> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text("Mark all as read")
                            }

                            state.render<NewsListState.Stable.Error> {
                                Spacer(Modifier.width(16.dp))
                                Text(
                                    text = "Failed",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { dispatch(NewsListAction.Error.ClickOk) }) {
                                    Text("OK")
                                }
                            }
                        }
                    }

                    items(renderState.newsList) { news ->
                        ListItem(
                            headlineContent = {
                                Text(news.title.ifEmpty { "News #${news.id}" })
                            },
                            trailingContent = {
                                if (news.isRead) {
                                    Text(
                                        "Read",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                }
                            },
                            modifier = Modifier.clickable {
                                dispatch(NewsListAction.ClickNews(id = news.id))
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
