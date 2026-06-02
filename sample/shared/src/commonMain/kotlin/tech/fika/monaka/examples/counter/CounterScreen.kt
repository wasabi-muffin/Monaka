package tech.fika.monaka.examples.counter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import tech.fika.monaka.ext.handleEffects
import tech.fika.monaka.ext.rememberStore
import tech.fika.monaka.ext.toViewStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CounterScreen(onBack: () -> Unit) {
    val store = rememberStore { scope -> CounterStateMachine(scope) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(store) {
        store.effects.collect { effect ->
            when (effect) {
                is CounterEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.text)
                is CounterEffect.SaveCount -> {
                    delay(300)
                    store.dispatch(CounterAction.SaveCompleted(true))
                }
            }
        }
    }

    val (state, dispatch) = store
        .handleEffects { effect ->
            when (effect) {
                is CounterEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.text)
                is CounterEffect.SaveCount -> {
                    delay(300)
                    store.dispatch(CounterAction.SaveCompleted(true))
                }
            }
        }
        .toViewStore()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Counter") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))

            Text(text = "${state.count}", style = MaterialTheme.typography.displayLarge)
            Text(
                text = "step: ${state.step}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(32.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(onClick = { dispatch(CounterAction.Decrement) }) {
                    Text("−", style = MaterialTheme.typography.titleLarge)
                }
                FilledTonalButton(onClick = { dispatch(CounterAction.Increment) }) {
                    Text("+", style = MaterialTheme.typography.titleLarge)
                }
            }

            Spacer(Modifier.height(32.dp))

            var stepInput by remember { mutableStateOf("${state.step}") }
            OutlinedTextField(
                value = stepInput,
                onValueChange = {
                    stepInput = it
                    stepInput.toIntOrNull()?.let { step -> dispatch(CounterAction.SetStep(step)) }
                },
                label = { Text("Step size") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                singleLine = true,
                modifier = Modifier.width(180.dp),
            )

            Spacer(Modifier.height(24.dp))

            OutlinedButton(onClick = { dispatch(CounterAction.Reset) }) { Text("Reset") }
        }
    }
}
