package dev.gmvalentino.monaka.scopes

import kotlinx.coroutines.CoroutineScope
import dev.gmvalentino.monaka.core.Action as ActionMarker
import dev.gmvalentino.monaka.core.Effect as EffectMarker
import dev.gmvalentino.monaka.core.State as StateMarker
import dev.gmvalentino.monaka.dsl.MonakaDsl
import dev.gmvalentino.monaka.handler.HandlerType
import dev.gmvalentino.monaka.runtime.JobRegistry

/**
 * Implicit receiver available inside `onError` hooks.
 *
 * Extends [AsyncHandlerScope] with the mapped [error] and the [handlerType] that was
 * being processed when the exception was thrown.
 *
 * The handler can inspect both to decide how to recover — e.g. transition to an
 * error state, emit an effect, or re-dispatch a retry action.
 *
 * @param machineScope A [CoroutineScope] tied to the state machine's lifetime.
 * @param state        The current state at the time the error was thrown.
 * @param error        The raw [Throwable] thrown by the handler or hook.
 * @param handlerType  Which handler origin threw the exception.
 * @param dispatch     The underlying dispatch function of the enclosing machine.
 * @param jobRegistry  The machine's [JobRegistry] for keyed job management.
 */
@MonakaDsl
public class ErrorScope<State : StateMarker, Action : ActionMarker, Effect : EffectMarker, SubState : State> internal constructor(
    override val machineScope: CoroutineScope,
    override val state: SubState,
    public val error: Throwable,
    public val handlerType: HandlerType<Action>,
    private val dispatch: (Action) -> Unit,
    jobRegistry: JobRegistry,
) : AsyncHandlerScope<State, Action, Effect, SubState>(
    machineScope = machineScope,
    state = state,
    internalDispatch = dispatch,
    jobRegistry = jobRegistry,
)
