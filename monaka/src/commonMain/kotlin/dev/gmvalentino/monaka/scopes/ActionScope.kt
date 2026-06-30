package dev.gmvalentino.monaka.scopes

import dev.gmvalentino.monaka.dsl.MonakaDsl
import dev.gmvalentino.monaka.runtime.JobRegistry
import kotlinx.coroutines.CoroutineScope
import dev.gmvalentino.monaka.core.Action as ActionMarker
import dev.gmvalentino.monaka.core.Effect as EffectMarker
import dev.gmvalentino.monaka.core.State as StateMarker

/**
 * Implicit receiver available inside `on<ActionType>` action handlers.
 *
 * Extends [HandlerScope] (not [AsyncHandlerScope]) and declares its own `task` overloads
 * with an [ActionTaskScope] receiver, so [ActionTaskScope.action] is directly available
 * inside the coroutine body without manual capture.
 *
 * Because [HandlerScope] declares no `task` at all, there is exactly one `task` signature
 * in this class — no overload-resolution ambiguity.
 *
 * @param machineScope A [CoroutineScope] tied to the state machine's lifetime.
 * @param dispatch     The underlying dispatch function of the enclosing machine.
 * @param jobRegistry  The machine's [JobRegistry] for keyed job management.
 */
@MonakaDsl
public class ActionScope<State : StateMarker, Action : ActionMarker, Effect : EffectMarker, SubState : State, ActionType : Action> internal constructor(
    override val machineScope: CoroutineScope,
    override val state: SubState,
    /** The action being processed in this handler invocation. */
    public val action: ActionType,
    private val dispatch: (Action) -> Unit,
    jobRegistry: JobRegistry,
) : HandlerScope<State, Action, Effect, SubState>(
    machineScope = machineScope,
    state = state,
    internalDispatch = dispatch,
    jobRegistry = jobRegistry,
) {

    /**
     * Launch a fire-and-forget coroutine with access to the typed [action].
     *
     * The lambda receiver is [ActionTaskScope], which exposes [ActionTaskScope.action]
     * typed as [ActionType] — no manual capture needed.
     *
     * Because [HandlerScope] declares no `task`, this is the only `task` signature
     * visible here. No overload ambiguity; Kotlin resolves it unconditionally.
     */
    public fun task(
        autoCancel: Boolean = false,
        coroutineScope: CoroutineScope = machineScope,
        block: suspend ActionTaskScope<State, Action, SubState, ActionType>.() -> Unit,
    ) {
        if (guarded || rejected) return
        val capturedState = state
        val capturedAction = action
        val capturedDispatch = internalDispatch
        jobRegistry.launch(scope = coroutineScope, autoCancel = autoCancel) {
            block(ActionTaskScope(this, capturedState, capturedAction, capturedDispatch))
        }
    }

    /** @see task */
    public fun task(
        key: String,
        autoCancel: Boolean = false,
        coroutineScope: CoroutineScope = machineScope,
        block: suspend ActionTaskScope<State, Action, SubState, ActionType>.() -> Unit,
    ) {
        if (guarded || rejected) return
        val capturedState = state
        val capturedAction = action
        val capturedDispatch = internalDispatch
        jobRegistry.launch(scope = coroutineScope, key = key, autoCancel = autoCancel) {
            block(ActionTaskScope(this, capturedState, capturedAction, capturedDispatch))
        }
    }
}
