package dev.gmvalentino.monaka.scopes

import kotlinx.coroutines.CoroutineScope
import dev.gmvalentino.monaka.core.Action as ActionMarker
import dev.gmvalentino.monaka.core.Effect as EffectMarker
import dev.gmvalentino.monaka.core.State as StateMarker
import dev.gmvalentino.monaka.dsl.MonakaDsl
import dev.gmvalentino.monaka.runtime.JobRegistry

/**
 * Implicit receiver available inside `onUpdate` hooks.
 *
 * Extends [AsyncHandlerScope] with [fromState] so that handlers can react to specific
 * field changes within the same state type.
 *
 * @param machineScope A [CoroutineScope] tied to the state machine's lifetime.
 * @param dispatch     The underlying dispatch function of the enclosing machine.
 * @param jobRegistry  The machine's [JobRegistry] for keyed job management.
 */
@MonakaDsl
public class StateUpdateScope<State : StateMarker, Action : ActionMarker, Effect : EffectMarker, SubState : State> internal constructor(
    override val machineScope: CoroutineScope,
    override val state: SubState,
    /** The previous state value before the same-type transition that triggered `onUpdate`. */
    public val fromState: SubState,
    private val dispatch: (Action) -> Unit,
    jobRegistry: JobRegistry,
) : AsyncHandlerScope<State, Action, Effect, SubState>(
    machineScope = machineScope,
    state = state,
    internalDispatch = dispatch,
    jobRegistry = jobRegistry,
)
