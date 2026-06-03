package tech.fika.monaka

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tech.fika.monaka.examples.checkout.checkout.CheckoutScreen
import tech.fika.monaka.examples.counter.CounterScreen
import tech.fika.monaka.examples.livefeed.LiveFeedScreen
import tech.fika.monaka.examples.login.LoginScreen
import tech.fika.monaka.examples.timer.TimerScreen

private enum class Screen { Home, Counter, Login, Checkout, LiveFeed, Timer }

@Composable
fun App() {
    MaterialTheme {
        var screen by remember { mutableStateOf(Screen.Home) }
        val back: () -> Unit = { screen = Screen.Home }
        when (screen) {
            Screen.Home -> HomeScreen(onSelect = { screen = it })
            Screen.Counter -> CounterScreen(onBack = back)
            Screen.Login -> LoginScreen(onBack = back)
            Screen.Checkout -> CheckoutScreen(onBack = back)
            Screen.LiveFeed -> LiveFeedScreen(onBack = back)
            Screen.Timer -> TimerScreen(onBack = back)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(onSelect: (Screen) -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Monaka Examples") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ExampleCard(Screen.Counter, "Counter",
                "Basic state machine: increment, decrement, step size, and reset.", onSelect)
            ExampleCard(Screen.Login, "Login",
                "Authentication flow with form validation and async credential check.", onSelect)
            ExampleCard(Screen.Checkout, "Checkout",
                "Three machines (Auth, Cart, Checkout) coordinated via StoreRegistry and Relays.", onSelect)
            ExampleCard(Screen.LiveFeed, "Live Feed",
                "Search with debounce, live polling, and exponential back-off retry.", onSelect)
            ExampleCard(Screen.Timer, "Countdown Timer",
                "Lifecycle-aware timer using onEnter/onExit for tick management and onPause/onResume for auto-pause.", onSelect)
        }
    }
}

@Composable
private fun ExampleCard(screen: Screen, title: String, description: String, onSelect: (Screen) -> Unit) {
    Card(
        onClick = { onSelect(screen) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
