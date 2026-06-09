package dev.gmvalentino.monaka.plugin

import dev.gmvalentino.monaka.core.Action as ActionMarker
import dev.gmvalentino.monaka.core.Effect as EffectMarker
import dev.gmvalentino.monaka.handler.HandlerType
import dev.gmvalentino.monaka.core.State as StateMarker

/**
 * Observer plugin that can hook into state machine lifecycle events.
 *
 * All methods have default no-op implementations; override only the hooks you need.
 * Plugins are called **synchronously** inside the processing coroutine, so keep
 * implementations fast. For expensive work (e.g., network analytics), launch a
 * separate coroutine inside the plugin.
 *
 * Install plugins via [dev.gmvalentino.monaka.dsl.StateMachineBuilder.install].
 *
 * ### Execution order
 * Plugins are called in installation order for every hook.
 *
 * ### Thread safety
 * Plugin methods are always called from the single sequential processing coroutine,
 * so no additional synchronisation is needed for plugin-internal mutable state.
 */
public interface Plugin<State : StateMarker, Action : ActionMarker, Effect : EffectMarker> {

    /**
     * Called just before an action is processed, on the sequential processing coroutine.
     *
     * [currentState] reflects the actual state at the moment the action is dequeued —
     * which may differ from the state at the time [dev.gmvalentino.monaka.core.Store.dispatch]
     * was called if other actions were queued ahead of it.
     *
     * Use this hook for logging or analytics that need to capture the pre-transition snapshot.
     */
    public fun onAction(currentState: State, action: Action): Unit = Unit

    public fun onEffect(effect: Effect): Unit = Unit

    /**
     * Called after a handler produces a [dev.gmvalentino.monaka.handler.HandlerResult.Transition] result.
     * Not called for [dev.gmvalentino.monaka.handler.HandlerResult.SideEffect], [dev.gmvalentino.monaka.handler.HandlerResult.Rejected],
     * or [dev.gmvalentino.monaka.handler.HandlerResult.Done].
     */
    public fun onTransition(fromState: State, toState: State): Unit = Unit

    /**
     * Called when no handler is registered for the [handlerType] in the current [currentState],
     * or when a handler produces a [dev.gmvalentino.monaka.handler.HandlerResult.Rejected] result.
     *
     * This is informational; no state change occurs.
     */
    public fun onRejected(currentState: State, handlerType: HandlerType<Action>): Unit = Unit

    /**
     * Called when a handler throws an exception.
     *
     * [handlerType] identifies where the error originated — an action handler,
     * a lifecycle hook, or a state-lifecycle hook (`onEnter` / `onExit` / `onUpdate`).
     *
     * The state is **not** changed when an error occurs. Implement this hook to log
     * or report errors. The processing loop continues after this call.
     */
    public fun onError(error: Throwable, currentState: State, handlerType: HandlerType<Action>): Unit = Unit
}
