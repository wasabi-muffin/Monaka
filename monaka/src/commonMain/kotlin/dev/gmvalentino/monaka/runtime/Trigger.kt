package dev.gmvalentino.monaka.runtime

import dev.gmvalentino.monaka.core.LifecycleEvent
import dev.gmvalentino.monaka.core.StateHook
import dev.gmvalentino.monaka.core.Action as ActionMarker
import dev.gmvalentino.monaka.core.State as StateMarker

/**
 * Items that flow through the internal [DefaultStore] processing channel.
 *
 * - [Action] — a user or system action dispatched via [dev.gmvalentino.monaka.core.Store.dispatch].
 * - [Lifecycle] — an application lifecycle event forwarded via [dev.gmvalentino.monaka.core.Store.onLifecycleEvent].
 * - [Hook] — a state-lifecycle hook fired directly via [dev.gmvalentino.monaka.core.Store.triggerStateHook],
 *   primarily for testing.
 * - [Restore] — an async state initializer enqueued by [DefaultStore.start] when an `initializer`
 *   was provided at construction. Runs before any queued actions so the restored state is always
 *   visible to the first action handler.
 *
 * Both type parameters are covariant so that [Action] and [Lifecycle] — which carry no state —
 * can use [Nothing] and still be stored in a `Channel<Trigger<State, Action>>`.
 */
internal sealed interface Trigger<out State : StateMarker, out Action : ActionMarker> {
    data class Action<out Action : ActionMarker>(val action: Action) : Trigger<Nothing, Action>
    data class Lifecycle(val event: LifecycleEvent) : Trigger<Nothing, Nothing>
    data class Hook<out State : StateMarker>(val hook: StateHook<State>) : Trigger<State, Nothing>

    /** Carries the suspend lambda that loads persisted state before `onEnter` fires. */
    class Restore<out State : StateMarker>(val initializer: suspend () -> State) : Trigger<State, Nothing>
}
