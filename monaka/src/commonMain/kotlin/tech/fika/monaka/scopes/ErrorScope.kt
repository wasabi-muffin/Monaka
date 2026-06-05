package tech.fika.monaka.scopes

import kotlinx.coroutines.CoroutineScope
import tech.fika.monaka.core.Action as ActionMarker
import tech.fika.monaka.core.Effect as EffectMarker
import tech.fika.monaka.core.State as StateMarker
import tech.fika.monaka.dsl.MonakaDsl
import tech.fika.monaka.handler.HandlerType
import tech.fika.monaka.runtime.JobRegistry

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
class ErrorScope<State : StateMarker, Action : ActionMarker, Effect : EffectMarker, SubState : State> internal constructor(
    override val machineScope: CoroutineScope,
    override val state: SubState,
    val error: Throwable,
    val handlerType: HandlerType<Action>,
    private val dispatch: (Action) -> Unit,
    jobRegistry: JobRegistry,
) : AsyncHandlerScope<State, Action, Effect, SubState>(
    machineScope = machineScope,
    state = state,
    internalDispatch = dispatch,
    jobRegistry = jobRegistry,
)
