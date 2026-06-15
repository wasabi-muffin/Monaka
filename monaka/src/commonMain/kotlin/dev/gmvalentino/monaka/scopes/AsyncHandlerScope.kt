package dev.gmvalentino.monaka.scopes

import kotlinx.coroutines.CoroutineScope
import dev.gmvalentino.monaka.core.Action as ActionMarker
import dev.gmvalentino.monaka.core.Effect as EffectMarker
import dev.gmvalentino.monaka.core.State as StateMarker
import dev.gmvalentino.monaka.dsl.MonakaDsl
import dev.gmvalentino.monaka.runtime.JobRegistry

/**
 * Extends [HandlerScope] with `task` support for scopes that do not carry a typed action
 * (lifecycle, state-change, error, and update handlers).
 *
 * [ActionScope] intentionally does **not** extend this class — it declares its own `task`
 * overloads with an [ActionTaskScope] receiver so that [ActionTaskScope.action] is
 * directly available inside the coroutine body without manual capture.
 *
 * Separating the two `task` signatures into distinct branches of the hierarchy
 * means each concrete scope exposes exactly one `task` overload, avoiding the
 * contravariance-driven overload-resolution ambiguity that would arise if both
 * signatures lived in the same class.
 */
@MonakaDsl
public abstract class AsyncHandlerScope<State : StateMarker, Action : ActionMarker, Effect : EffectMarker, SubState : State> internal constructor(
    machineScope: CoroutineScope,
    state: SubState,
    internalDispatch: (Action) -> Unit,
    jobRegistry: JobRegistry,
) : HandlerScope<State, Action, Effect, SubState>(
    machineScope = machineScope,
    state = state,
    internalDispatch = internalDispatch,
    jobRegistry = jobRegistry,
) {
    /**
     * Launch a fire-and-forget coroutine in [coroutineScope] (defaults to [machineScope]).
     *
     * The lambda receives a [TaskScope] which exposes [TaskScope.state] and
     * [TaskScope.dispatch]. Because [TaskScope] carries the `@MonakaDsl` marker,
     * implicit access to the outer receiver's [transition], [sideEffect], [reject], and
     * [guard] is a **compile error** inside the task body.
     *
     * When [autoCancel] is true, the job is canceled on the next state-type change
     * (before the corresponding `onExit` hook fires). No-op if [reject] has already been called.
     *
     * **Exception handling:** uncaught exceptions thrown inside [block] propagate to
     * [coroutineScope] and are **not** forwarded to plugins via `onError`. To route
     * task errors through the machine's error handling, catch inside [block] and call
     * [TaskScope.dispatch] with a dedicated error action instead.
     */
    public fun task(
        autoCancel: Boolean = false,
        coroutineScope: CoroutineScope = machineScope,
        block: suspend TaskScope<State, Action, SubState>.() -> Unit,
    ) {
        if (guarded || rejected) return
        val capturedState = state
        val capturedDispatch = internalDispatch
        jobRegistry.launch(scope = coroutineScope, autoCancel = autoCancel) {
            block(TaskScope(this, capturedState, capturedDispatch))
        }
    }

    /**
     * Cancel any job previously registered under [key], then launch a new keyed coroutine
     * in [coroutineScope] (defaults to [machineScope]) registered under [key].
     *
     * Use for debounce and "latest wins" patterns. When [autoCancel] is true, the job is
     * additionally canceled (and its key unregistered) on the next state-type change.
     * No-op if [reject] or [guard] has already been called.
     *
     * **Exception handling:** same as the unkeyed overload — uncaught exceptions inside
     * [block] are **not** forwarded to plugins. Catch and dispatch an error action to
     * route failures through the machine's error handling.
     */
    public fun task(
        key: String,
        autoCancel: Boolean = false,
        coroutineScope: CoroutineScope = machineScope,
        block: suspend TaskScope<State, Action, SubState>.() -> Unit,
    ) {
        if (guarded || rejected) return
        val capturedState = state
        val capturedDispatch = internalDispatch
        jobRegistry.launch(scope = coroutineScope, key = key, autoCancel = autoCancel) {
            block(TaskScope(this, capturedState, capturedDispatch))
        }
    }
}
