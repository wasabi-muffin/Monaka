package dev.gmvalentino.monaka

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.gmvalentino.monaka.examples.checkout.checkout.CheckoutScreen
import dev.gmvalentino.monaka.examples.counter.CounterScreen
import dev.gmvalentino.monaka.examples.livefeed.LiveFeedScreen
import dev.gmvalentino.monaka.examples.login.LoginScreen
import dev.gmvalentino.monaka.examples.news.NewsGraph
import dev.gmvalentino.monaka.examples.news.details.NewsDetailsScreen
import dev.gmvalentino.monaka.examples.news.details.NewsDetailsViewModel
import dev.gmvalentino.monaka.examples.news.list.NewsListScreen
import dev.gmvalentino.monaka.examples.timer.TimerScreen
import dev.zacsweers.metro.createGraph
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel

@Composable
fun App() {
    MaterialTheme {
        val newsGraph = remember { createGraph<NewsGraph>() }
        val backStack = remember { NavBackStack<Screen>(Screen.Home) }
        CompositionLocalProvider(LocalMetroViewModelFactory provides newsGraph.metroViewModelFactory) {
            NavDisplay(
                backStack = backStack,
                onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(viewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current))
                ),
                entryProvider = entryProvider {
                    entry<Screen.Home> {
                        HomeScreen(onSelect = { screen -> backStack.add(screen) })
                    }
                    entry<Screen.Counter> {
                        CounterScreen(onBack = { backStack.removeLastOrNull() })
                    }
                    entry<Screen.Login> {
                        LoginScreen(onBack = { backStack.removeLastOrNull() })
                    }
                    entry<Screen.Checkout> {
                        CheckoutScreen(onBack = { backStack.removeLastOrNull() })
                    }
                    entry<Screen.LiveFeed> {
                        LiveFeedScreen(onBack = { backStack.removeLastOrNull() })
                    }
                    entry<Screen.Timer> {
                        TimerScreen(onBack = { backStack.removeLastOrNull() })
                    }
                    entry<Screen.NewsList> {
                        NewsListScreen(
                            onNavigateToDetails = { id -> backStack.add(Screen.NewsDetails(id)) },
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                    entry<Screen.NewsDetails> {
                        val id = it.id
                        NewsDetailsScreen(
                            onBack = { backStack.removeLastOrNull() },
                            viewModel = assistedMetroViewModel<NewsDetailsViewModel, NewsDetailsViewModel.Factory> { create(id) },
                        )
                    }
                },
            )
        }
    }
}
