package dev.gmvalentino.monaka.compose

import dev.gmvalentino.monaka.core.Action
import dev.gmvalentino.monaka.core.Effect
import dev.gmvalentino.monaka.core.State
import dev.gmvalentino.monaka.core.Store
import dev.gmvalentino.monaka.dsl.store
import kotlinx.coroutines.CoroutineScope

internal sealed interface FixtureState : State {
    data class Count(val value: Int) : FixtureState
    data object Loading : FixtureState
}

internal sealed interface FixtureAction : Action {
    data object Increment : FixtureAction
    data object Emit : FixtureAction
}

internal sealed interface FixtureEffect : Effect {
    data object Pinged : FixtureEffect
}

internal fun counterStore(scope: CoroutineScope): Store<FixtureState, FixtureAction, FixtureEffect> =
    store(scope = scope) {
        initialState(FixtureState.Count(0))
        state<FixtureState.Count> {
            on<FixtureAction.Increment> { transition(FixtureState.Count(state.value + 1)) }
            on<FixtureAction.Emit> { sideEffect(FixtureEffect.Pinged) }
        }
    }
