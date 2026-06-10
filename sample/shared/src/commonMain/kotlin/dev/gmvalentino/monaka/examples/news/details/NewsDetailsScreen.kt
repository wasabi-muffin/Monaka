package dev.gmvalentino.monaka.examples.news.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gmvalentino.monaka.ext.bindLifecycle
import dev.gmvalentino.monaka.ext.handleEffects
import dev.gmvalentino.monaka.ext.render
import dev.gmvalentino.monaka.ext.toViewStore
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsDetailsScreen(
    onBack: () -> Unit,
    viewModel: NewsDetailsViewModel,
) {
    val (state, dispatch) = viewModel.store
        .bindLifecycle()
        .handleEffects { effect ->
            when (effect) {
                NewsDetailsEffect.NavigateBack -> onBack()
            }
        }
        .toViewStore()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("News Details") },
                navigationIcon = {
                    IconButton(onClick = { dispatch(NewsDetailsAction.ClickBack) }) {
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
            state.render<NewsDetailsState.InitialLoading> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            state.render<NewsDetailsState.Error> {
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
                        Text("Failed to load details", style = MaterialTheme.typography.bodyLarge)
                        Button(onClick = { dispatch(NewsDetailsAction.Error.ClickRetry) }) {
                            Text("Retry")
                        }
                    }
                }
            }

            state.render<NewsDetailsState.Stable> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = renderState.newsDetails.title.ifEmpty { "News #${renderState.newsDetails.id}" },
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        text = renderState.newsDetails.body.ifEmpty { "No content available." },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
