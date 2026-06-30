package dev.gmvalentino.monaka.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.gmvalentino.monaka.core.Store
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import dev.gmvalentino.monaka.core.Action as ActionMarker
import dev.gmvalentino.monaka.core.Effect as EffectMarker
import dev.gmvalentino.monaka.core.State as StateMarker

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
public fun <State : StateMarker, Action : ActionMarker, Effect : EffectMarker> Store<State, Action, Effect>.handleEffects(
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
