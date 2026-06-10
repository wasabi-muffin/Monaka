package dev.gmvalentino.monaka.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import dev.gmvalentino.monaka.core.Action as ActionMarker
import dev.gmvalentino.monaka.core.Effect as EffectMarker
import dev.gmvalentino.monaka.core.State as StateMarker
import dev.gmvalentino.monaka.core.Store

/**
 * Create a [Store] tied to this composable's lifetime — built once via [factory] and
 * canceled when the composable leaves the composition. Replaces the Android-only
 * `ViewModel` + `viewModel()` pattern with one that works on every CMP target.
 */
@Composable
public fun <S : StateMarker, A : ActionMarker, E : EffectMarker> rememberStore(
    factory: (CoroutineScope) -> Store<S, A, E>,
): Store<S, A, E> {
    val scope = rememberCoroutineScope()
    val store = remember(scope) { factory(scope) }
    DisposableEffect(store) { onDispose { store.stop() } }
    return store
}
