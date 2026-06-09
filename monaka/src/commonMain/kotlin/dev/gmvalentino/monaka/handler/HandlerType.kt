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
sealed interface HandlerType<out Action : ActionMarker> {
    data class Action<out Action : ActionMarker>(val action: Action) : HandlerType<Action>
    data class Lifecycle(val event: LifecycleEvent) : HandlerType<Nothing>
    sealed interface Hook : HandlerType<Nothing>{
        data object Enter : Hook
        data object Exit : Hook
        data object Update : Hook
    }
}
