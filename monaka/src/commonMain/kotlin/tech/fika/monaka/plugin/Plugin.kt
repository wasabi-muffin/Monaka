package tech.fika.monaka.plugin

import tech.fika.monaka.core.Action as ActionMarker
import tech.fika.monaka.core.Effect as EffectMarker
import tech.fika.monaka.handler.HandlerType
import tech.fika.monaka.core.State as StateMarker

/**
 * Observer plugin that can hook into state machine lifecycle events.
 *
 * All methods have default no-op implementations; override only the hooks you need.
 * Plugins are called **synchronously** inside the processing coroutine, so keep
 * implementations fast. For expensive work (e.g., network analytics), launch a
 * separate coroutine inside the plugin.
 *
 * Install plugins via [tech.fika.monaka.dsl.StateMachineBuilder.install].
 *
 * ### Execution order
 * Plugins are called in installation order for every hook.
 *
 * ### Thread safety
 * Plugin methods are always called from the single sequential processing coroutine,
 * so no additional synchronisation is needed for plugin-internal mutable state.
 */
interface Plugin<State : StateMarker, Action : ActionMarker, Effect : EffectMarker> {

    /**
     * Called immediately when an action is dispatched, **before** it is processed.
     *
     * At this point the state has not yet changed. Use this hook for logging or
     * analytics that need to capture the "before" snapshot.
     */
    fun onAction(currentState: State, action: Action): Unit = Unit

    fun onEffect(effect: Effect): Unit = Unit

    /**
     * Called after a handler produces a [tech.fika.monaka.handler.HandlerResult.Transition] result.
     * Not called for [tech.fika.monaka.handler.HandlerResult.SideEffect], [tech.fika.monaka.handler.HandlerResult.Rejected],
     * or [tech.fika.monaka.handler.HandlerResult.Done].
     */
    fun onTransition(fromState: State, toState: State): Unit = Unit

    /**
     * Called when no handler is registered for the [handlerType] in the current [currentState],
     * or when a handler produces a [tech.fika.monaka.handler.HandlerResult.Rejected] result.
     *
     * This is informational; no state change occurs.
     */
    fun onRejected(currentState: State, handlerType: HandlerType<Action>): Unit = Unit

    /**
     * Called when a handler throws an exception.
     *
     * [handlerType] identifies where the error originated — an action handler,
     * a lifecycle hook, or a state-lifecycle hook (`onEnter` / `onExit` / `onUpdate`).
     *
     * The state is **not** changed when an error occurs. Implement this hook to log
     * or report errors. The processing loop continues after this call.
     */
    fun onError(error: Throwable, currentState: State, handlerType: HandlerType<Action>): Unit = Unit
}
