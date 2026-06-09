package dev.gmvalentino.monaka.core

/**
 * Default [kotlinx.coroutines.flow.SharedFlow] `extraBufferCapacity` used for the effects
 * and actions flows in every [Store].
 *
 * Both flows are created with this capacity so that rapid bursts of effects or handler-initiated
 * [Store.dispatch] calls do not suspend the processing coroutine.
 *
 * Pass a larger value via the `extraBufferCapacity` parameter on
 * [dev.gmvalentino.monaka.dsl.store] or [dev.gmvalentino.monaka.dsl.StateMachineStore]
 * if your machine emits effects in rapid bursts.
 */
public const val DEFAULT_BUFFER_CAPACITY: Int = 64
