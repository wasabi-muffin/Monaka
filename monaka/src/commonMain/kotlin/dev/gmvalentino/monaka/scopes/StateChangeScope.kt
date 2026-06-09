package dev.gmvalentino.monaka.scopes

import kotlinx.coroutines.CoroutineScope
import dev.gmvalentino.monaka.core.Action as ActionMarker
import dev.gmvalentino.monaka.core.Effect as EffectMarker
import dev.gmvalentino.monaka.core.State as StateMarker
import dev.gmvalentino.monaka.dsl.MonakaDsl
import dev.gmvalentino.monaka.runtime.JobRegistry

/**
 * Implicit receiver available inside state change hooks (`onEnter`, `onExit`).
 *
 * Extends [AsyncHandlerScope]. State-change hooks do not originate from a dispatched
 * action, so there is no `action` property.
 *
 * @param machineScope A [CoroutineScope] tied to the state machine's lifetime.
 * @param dispatch     The underlying dispatch function of the enclosing machine.
 * @param jobRegistry  The machine's [JobRegistry] for keyed job management.
 */
@MonakaDsl
public class StateChangeScope<State : StateMarker, Action : ActionMarker, Effect : EffectMarker, SubState : State> internal constructor(
    override val machineScope: CoroutineScope,
    override val state: SubState,
    private val dispatch: (Action) -> Unit,
    jobRegistry: JobRegistry,
) : AsyncHandlerScope<State, Action, Effect, SubState>(
    machineScope = machineScope,
    state = state,
    internalDispatch = dispatch,
    jobRegistry = jobRegistry,
)
