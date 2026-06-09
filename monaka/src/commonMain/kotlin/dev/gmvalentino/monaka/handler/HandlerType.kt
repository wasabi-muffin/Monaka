package dev.gmvalentino.monaka.handler

import dev.gmvalentino.monaka.core.Action as ActionMarker
import dev.gmvalentino.monaka.core.LifecycleEvent

/**
 * Identifies the origin of a handler invocation.
 *
 * Used by [dev.gmvalentino.monaka.plugin.Plugin.onError] to give plugins enough
 * context to log or report an error meaningfully.
 *
 * Five variants cover every place the runtime calls a handler lambda:
 * - [Action]     — an action dispatched via [dev.gmvalentino.monaka.core.Store.dispatch].
 * - [Lifecycle]  — an application lifecycle event forwarded via
 *                  [dev.gmvalentino.monaka.core.Store.onLifecycleEvent].
 * - [Hook.Enter]  — the `onEnter` block for a state.
 * - [Hook.Exit]   — the `onExit` block for a state.
 * - [Hook.Update] — the `onUpdate` block for a state.
 */
public sealed interface HandlerType<out Action : ActionMarker> {
    public data class Action<out Action : ActionMarker>(public val action: Action) : HandlerType<Action>
    public data class Lifecycle(public val event: LifecycleEvent) : HandlerType<Nothing>
    public sealed interface Hook : HandlerType<Nothing>{
        public data object Enter : Hook
        public data object Exit : Hook
        public data object Update : Hook
    }
}
