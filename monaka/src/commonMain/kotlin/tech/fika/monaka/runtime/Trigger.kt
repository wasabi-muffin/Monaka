package tech.fika.monaka.runtime

import tech.fika.monaka.core.Action as ActionMarker
import tech.fika.monaka.core.LifecycleEvent
import tech.fika.monaka.core.StateHook

/**
 * Items that flow through the internal [DefaultStore] processing channel.
 *
 * - [Action] — a user or system action dispatched via [tech.fika.monaka.core.Store.dispatch].
 * - [Lifecycle] — an application lifecycle event forwarded via [tech.fika.monaka.core.Store.onLifecycleEvent].
 * - [Hook] — a state-lifecycle hook fired directly via [tech.fika.monaka.core.Store.triggerStateHook],
 *   primarily for testing.
 */
internal sealed interface Trigger<out Action : ActionMarker> {
    data class Action<out Action : ActionMarker>(val action: Action) : Trigger<Action>
    data class Lifecycle(val event: LifecycleEvent) : Trigger<Nothing>
    data class Hook(val hook: StateHook) : Trigger<Nothing>
}
