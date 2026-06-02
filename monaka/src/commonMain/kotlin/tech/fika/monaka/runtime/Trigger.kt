package tech.fika.monaka.runtime

import tech.fika.monaka.core.Action as ActionMarker
import tech.fika.monaka.core.LifecycleEvent

/**
 * Items that flow through the internal [DefaultStore] processing channel.
 *
 * Only two variants exist because [Trigger] represents things that can be
 * *dispatched into* the store from the outside world:
 * - [Action] — a user or system action.
 * - [Lifecycle] — an application lifecycle event.
 *
 * State-lifecycle notifications ([tech.fika.monaka.handler.HandlerType.Hook.Enter],
 * [tech.fika.monaka.handler.HandlerType.Hook.Exit], [tech.fika.monaka.handler.HandlerType.Hook.Update])
 * are fired internally by the runtime and never travel through the channel.
 */
internal sealed interface Trigger<out Action : ActionMarker> {
    data class Action<out Action : ActionMarker>(val action: Action) : Trigger<Action>
    data class Lifecycle(val event: LifecycleEvent) : Trigger<Nothing>
}
