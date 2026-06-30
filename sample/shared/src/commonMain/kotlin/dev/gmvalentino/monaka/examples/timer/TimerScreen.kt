package dev.gmvalentino.monaka.examples.timer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.gmvalentino.monaka.compose.bindLifecycle
import dev.gmvalentino.monaka.compose.handleEffects
import dev.gmvalentino.monaka.compose.rememberStore
import dev.gmvalentino.monaka.compose.render
import dev.gmvalentino.monaka.compose.toViewStore
import dev.gmvalentino.monaka.dsl.store

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(onBack: () -> Unit) {
    val store = rememberStore { scope ->
        store(stateMachine = TimerStateMachine(), scope = scope)
    }
    val snackbarHostState = remember { SnackbarHostState() }

    val (state, dispatch) = store
        .bindLifecycle()
        .handleEffects { effect ->
            if (effect is TimerEffect.Completed) {
                snackbarHostState.showSnackbar("Time's up!")
            }
        }
        .toViewStore()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Countdown Timer") },
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
            verticalArrangement = Arrangement.Center,
        ) {
            // ── Timer display ─────────────────────────────────────────────────
            TimerDisplay(state = state)

            Spacer(Modifier.height(48.dp))

            // ── Duration input (only while idle) ──────────────────────────────
            state.render<TimerState.Idle> {
                DurationInput(
                    durationSeconds = renderState.durationSeconds,
                    onDurationChange = { dispatch(TimerAction.SetDuration(it)) },
                )
                Spacer(Modifier.height(24.dp))
            }

            // ── Auto-pause checkbox ───────────────────────────────────────────
            AutoPauseRow(
                checked = state.autoPause,
                onCheckedChange = { dispatch(TimerAction.SetAutoPause(it)) },
            )

            Spacer(Modifier.height(32.dp))

            // ── Controls ──────────────────────────────────────────────────────
            TimerControls(
                state = state,
                onStart = { dispatch(TimerAction.Start) },
                onPause = { dispatch(TimerAction.Pause) },
                onResume = { dispatch(TimerAction.Resume) },
                onReset = { dispatch(TimerAction.Reset) },
            )
        }
    }
}

// ── Timer display ─────────────────────────────────────────────────────────────

@Composable
private fun TimerDisplay(state: TimerState) {
    val timeString = when (state) {
        is TimerState.Idle -> state.durationSeconds.toTimerString()
        is TimerState.Running -> state.remainingSeconds.toTimerString()
        is TimerState.Paused -> state.remainingSeconds.toTimerString()
        is TimerState.Finished -> "00:00"
    }
    val progress = when (state) {
        is TimerState.Running -> state.progress
        is TimerState.Paused -> state.progress
        else -> 1f
    }
    val label = when (state) {
        is TimerState.Idle -> "Set duration below"
        is TimerState.Running -> "Running"
        is TimerState.Paused -> if (state.pausedByLifecycle) "Paused (background)" else "Paused"
        is TimerState.Finished -> "Finished!"
    }

    Box(contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.size(200.dp),
            strokeWidth = 8.dp,
            color = when (state) {
                is TimerState.Finished -> MaterialTheme.colorScheme.secondary
                is TimerState.Paused -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.primary
            },
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = timeString,
                style = MaterialTheme.typography.displayMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ── Duration input ────────────────────────────────────────────────────────────

@Composable
private fun DurationInput(durationSeconds: Int, onDurationChange: (Int) -> Unit) {
    var text by rememberSaveable(durationSeconds) { mutableStateOf(durationSeconds.toString()) }

    OutlinedTextField(
        value = text,
        onValueChange = { value ->
            text = value
            value.toIntOrNull()?.let { onDurationChange(it) }
        },
        label = { Text("Duration (seconds)") },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done,
        ),
        singleLine = true,
        modifier = Modifier.width(220.dp),
    )
}

// ── Auto-pause checkbox ───────────────────────────────────────────────────────

@Composable
private fun AutoPauseRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Column {
            Text("Auto-pause on background", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Pauses when app goes to background; resumes when it returns",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Controls ──────────────────────────────────────────────────────────────────

@Composable
private fun TimerControls(
    state: TimerState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReset: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        when (state) {
            is TimerState.Idle -> {
                Button(onClick = onStart) { Text("Start") }
            }

            is TimerState.Running -> {
                Button(onClick = onPause) { Text("Pause") }
                OutlinedButton(onClick = onReset) { Text("Stop") }
            }

            is TimerState.Paused -> {
                Button(onClick = onResume) { Text("Resume") }
                OutlinedButton(onClick = onReset) { Text("Stop") }
            }

            is TimerState.Finished -> {
                Button(onClick = onReset) { Text("Restart") }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun Int.toTimerString(): String {
    val minutes = this / 60
    val seconds = this % 60
    return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
}
