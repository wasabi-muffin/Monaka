package dev.gmvalentino.monaka.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gmvalentino.monaka.core.Store
import dev.gmvalentino.monaka.core.Action as ActionMarker
import dev.gmvalentino.monaka.core.Effect as EffectMarker
import dev.gmvalentino.monaka.core.State as StateMarker

@Immutable
public data class ViewStore<State : StateMarker, Action : ActionMarker>(
    val state: State,
    val dispatch: (Action) -> Unit,
)

@Composable
public fun <State : StateMarker, Action : ActionMarker, Effect : EffectMarker> Store<State, Action, Effect>.toViewStore(): ViewStore<State, Action> {
    val state by state.collectAsStateWithLifecycle()
    return ViewStore(state = state, dispatch = ::dispatch)
}
