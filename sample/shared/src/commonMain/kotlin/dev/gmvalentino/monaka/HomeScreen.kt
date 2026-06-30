package dev.gmvalentino.monaka

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreen(onSelect: (Screen) -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Monaka Examples") }) },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
        ) {
            ExampleCard(
                screen = Screen.Counter,
                title = "Counter",
                description = "Basic state machine: increment, decrement, step size, and reset.",
                onSelect = onSelect,
            )
            ExampleCard(
                screen = Screen.Login,
                title = "Login",
                description = "Authentication flow with form validation and async credential check.",
                onSelect = onSelect,
            )
            ExampleCard(
                screen = Screen.Checkout,
                title = "Checkout",
                description = "Three machines (Auth, Cart, Checkout) coordinated via StoreRegistry and Relays.",
                onSelect = onSelect,
            )
            ExampleCard(
                screen = Screen.LiveFeed,
                title = "Live Feed",
                description = "Search with debounce, live polling, and exponential back-off retry.",
                onSelect = onSelect,
            )
            ExampleCard(
                screen = Screen.Timer,
                title = "Countdown Timer",
                description = "Lifecycle-aware timer using onEnter/onExit for tick management and onPause/onResume for auto-pause.",
                onSelect = onSelect,
            )
            ExampleCard(
                screen = Screen.NewsList,
                title = "News (Metro DI)",
                description = "News list and details wired with Metro DI — ViewModels injected via metroViewModel() and assistedMetroViewModel().",
                onSelect = onSelect,
            )
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
