package dev.gmvalentino.monaka.plugin

import dev.gmvalentino.monaka.core.Action
import dev.gmvalentino.monaka.core.Effect
import dev.gmvalentino.monaka.handler.HandlerType
import dev.gmvalentino.monaka.core.State

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
public interface Plugin {

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

    /** Called after an effect is emitted. Use this hook for analytics or side-effect monitoring. */
    public fun onEffect(effect: Effect): Unit = Unit

    /**
     * Called after a handler produces a [dev.gmvalentino.monaka.handler.HandlerResult.Transition] result.
     * Not called for [dev.gmvalentino.monaka.handler.HandlerResult.SideEffect], [dev.gmvalentino.monaka.handler.HandlerResult.Rejected],
     * or [dev.gmvalentino.monaka.handler.HandlerResult.Done].
     */
    public fun onTransition(fromState: State, toState: State): Unit = Unit

    /**
     * Called when [action] arrives in [currentState] but no `on<>` handler is registered for it.
     *
     * This fires **before** [onRejected] and only for the "missing handler" case — it is not
     * called when a handler explicitly calls `reject()`. Use this hook to enforce action
     * exhaustiveness (e.g. throw in tests) or to log missing coverage in production.
     *
     * This is informational; no state change occurs.
     */
    public fun onUnhandled(currentState: State, action: Action): Unit = Unit

    /**
     * Called when a handler produces a [dev.gmvalentino.monaka.handler.HandlerResult.Rejected] result
     * (i.e. the handler explicitly called `reject()`).
     *
     * This is distinct from [onUnhandled], which fires when no handler is registered at all.
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
