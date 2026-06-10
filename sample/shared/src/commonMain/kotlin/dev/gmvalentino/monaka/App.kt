package dev.gmvalentino.monaka

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import dev.gmvalentino.monaka.examples.checkout.checkout.CheckoutScreen
import dev.gmvalentino.monaka.examples.counter.CounterScreen
import dev.gmvalentino.monaka.examples.livefeed.LiveFeedScreen
import dev.gmvalentino.monaka.examples.login.LoginScreen
import dev.gmvalentino.monaka.examples.timer.TimerScreen

@Composable
fun App() {
    MaterialTheme {
        val backStack = remember { NavBackStack<Screen>(Screen.Home) }
        NavDisplay(
            backStack = backStack,
            onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
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
            },
        )
    }
}
