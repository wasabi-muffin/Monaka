package tech.fika.monaka.runtime

import tech.fika.monaka.core.Action as ActionMarker
import tech.fika.monaka.core.LifecycleEvent
import tech.fika.monaka.core.State as StateMarker
import tech.fika.monaka.core.StateHook

/**
 * Items that flow through the internal [DefaultStore] processing channel.
 *
 * - [Action] — a user or system action dispatched via [tech.fika.monaka.core.Store.dispatch].
 * - [Lifecycle] — an application lifecycle event forwarded via [tech.fika.monaka.core.Store.onLifecycleEvent].
 * - [Hook] — a state-lifecycle hook fired directly via [tech.fika.monaka.core.Store.triggerStateHook],
 *   primarily for testing.
 *
 * Both type parameters are covariant so that [Action] and [Lifecycle] — which carry no state —
 * can use [Nothing] and still be stored in a `Channel<Trigger<State, Action>>`.
 */
internal sealed interface Trigger<out State : StateMarker, out Action : ActionMarker> {
    data class Action<out Action : ActionMarker>(val action: Action) : Trigger<Nothing, Action>
    data class Lifecycle(val event: LifecycleEvent) : Trigger<Nothing, Nothing>
    data class Hook<out State : StateMarker>(val hook: StateHook<State>) : Trigger<State, Nothing>
}
