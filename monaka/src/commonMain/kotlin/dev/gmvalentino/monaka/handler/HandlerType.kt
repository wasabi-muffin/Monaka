package dev.gmvalentino.monaka.handler

import dev.gmvalentino.monaka.core.LifecycleEvent
import dev.gmvalentino.monaka.core.Action as ActionMarker

/**
 * Identifies the origin of a handler invocation.
 *
 * Used by [dev.gmvalentino.monaka.plugin.Plugin.onError] to give plugins enough
 * context to log or report an error meaningfully.
 *
 * Six variants cover every place the runtime calls a handler lambda:
 * - [Action]     — an action dispatched via [dev.gmvalentino.monaka.core.Store.dispatch].
 * - [Lifecycle]  — an application lifecycle event forwarded via
 *                  [dev.gmvalentino.monaka.core.Store.onLifecycleEvent].
 * - [Hook.Enter]  — the `onEnter` block for a state.
 * - [Hook.Exit]   — the `onExit` block for a state.
 * - [Hook.Update] — the `onUpdate` block for a state.
 * - [Restore]    — the async `initializer` passed to [dev.gmvalentino.monaka.dsl.store] or
 *                  [dev.gmvalentino.monaka.dsl.StateMachineStore]. Reported to plugins via
 *                  [dev.gmvalentino.monaka.plugin.Plugin.onError] if the initializer throws.
 */
public sealed interface HandlerType<out Action : ActionMarker> {
    /** Wraps the [action] that triggered the handler. */
    public data class Action<out Action : ActionMarker>(public val action: Action) : HandlerType<Action>

    /** Wraps the [event] that triggered the lifecycle hook. */
    public data class Lifecycle(public val event: LifecycleEvent) : HandlerType<Nothing>

    /** Marker for state-lifecycle hook origins (`onEnter`, `onExit`, `onUpdate`). */
    public sealed interface Hook : HandlerType<Nothing> {
        /** The handler was triggered by an `onEnter` hook. */
        public data object Enter : Hook

        /** The handler was triggered by an `onExit` hook. */
        public data object Exit : Hook

        /** The handler was triggered by an `onUpdate` hook. */
        public data object Update : Hook
    }

    /**
     * The error originated in the async state initializer supplied at store construction.
     *
     * When the initializer throws, plugins receive this [HandlerType] via
     * [dev.gmvalentino.monaka.plugin.Plugin.onError]. The store continues with its
     * configured `initialState` and fires `onEnter` normally.
     */
    public data object Restore : HandlerType<Nothing>
}
