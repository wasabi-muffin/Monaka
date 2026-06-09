package dev.gmvalentino.monaka.ext

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import dev.gmvalentino.monaka.core.Action as ActionMarker
import dev.gmvalentino.monaka.core.Effect as EffectMarker
import dev.gmvalentino.monaka.core.LifecycleEvent as MonakaLifecycle
import dev.gmvalentino.monaka.core.State as StateMarker
import dev.gmvalentino.monaka.core.Store

@Immutable
data class ViewStore<State : StateMarker, Action : ActionMarker>(
    val state: State,
    val dispatch: (Action) -> Unit = {},
)

class RenderScope<State : StateMarker>(val renderState: State)

inline fun <reified State : StateMarker> StateMarker.render(block: RenderScope<State>.() -> Unit) {
    if (this is State) RenderScope(renderState = this).block()
}

@Composable
fun <State : StateMarker, Action : ActionMarker, Effect : EffectMarker> Store<State, Action, Effect>.toViewStore(): ViewStore<State, Action> {
    val state by state.collectAsStateWithLifecycle()
    return ViewStore(state = state, dispatch = ::dispatch)
}

/**
 * Collect one-shot [effects][Store.effects] in a lifecycle-aware manner.
 *
 * A dedicated coroutine **always** collects from the [Store]'s `SharedFlow` into an
 * internal [Channel], so no effect is ever lost due to buffer overflow or late
 * subscription. The [block] lambda, however, only drains that channel while the
 * lifecycle is at least [Lifecycle.State.STARTED] — effects emitted while the UI
 * is stopped (backgrounded, configuration change) are buffered and delivered in
 * order once the screen returns to the foreground.
 */
@Composable
fun <State : StateMarker, Action : ActionMarker, Effect : EffectMarker> Store<State, Action, Effect>.handleEffects(
    block: suspend (Effect) -> Unit,
): Store<State, Action, Effect> {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(this) {
        val buffer = Channel<Effect>(Channel.UNLIMITED)
        launch { effects.collect { buffer.send(it) } }
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            for (effect in buffer) {
                block(effect)
            }
        }
    }
    return this
}

/**
 * Observe the platform lifecycle and forward each event to this [Store] as a
 * [dev.gmvalentino.monaka.core.LifecycleEvent], enabling `onResume`, `onPause`, and other
 * lifecycle hooks registered in the state machine to fire automatically.
 *
 * Uses `org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose`, JetBrains'
 * multiplatform fork of AndroidX lifecycle. The same `LocalLifecycleOwner` works
 * on Android (host Activity/Fragment) and iOS (provided by `ComposeUIViewController`).
 */
@Composable
fun <State : StateMarker, Action : ActionMarker, Effect : EffectMarker> Store<State, Action, Effect>.bindLifecycle(): Store<State, Action, Effect> {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, this) {
        val observer = LifecycleEventObserver { _, event ->
            event.toMonakaLifecycleEvent()?.let(::onLifecycleEvent)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return this
}

private fun Lifecycle.Event.toMonakaLifecycleEvent() = when (this) {
    Lifecycle.Event.ON_CREATE -> MonakaLifecycle.OnCreate
    Lifecycle.Event.ON_START -> MonakaLifecycle.OnStart
    Lifecycle.Event.ON_RESUME -> MonakaLifecycle.OnResume
    Lifecycle.Event.ON_PAUSE -> MonakaLifecycle.OnPause
    Lifecycle.Event.ON_STOP -> MonakaLifecycle.OnStop
    Lifecycle.Event.ON_DESTROY -> MonakaLifecycle.OnDestroy
    else -> null
}

/**
 * Create a [Store] tied to this composable's lifetime — built once via [factory] and
 * canceled when the composable leaves the composition. Replaces the Android-only
 * `ViewModel` + `viewModel()` pattern with one that works on every CMP target.
 */
@Composable
fun <S : StateMarker, A : ActionMarker, E : EffectMarker> rememberStore(
    factory: (CoroutineScope) -> Store<S, A, E>,
): Store<S, A, E> {
    val scope = rememberCoroutineScope()
    val store = remember(scope) { factory(scope) }
    DisposableEffect(store) { onDispose { store.stop() } }
    return store
}
