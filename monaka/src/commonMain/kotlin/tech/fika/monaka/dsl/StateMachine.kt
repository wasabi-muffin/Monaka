package tech.fika.monaka.dsl

import kotlin.reflect.KClass
import tech.fika.monaka.core.Action as ActionMarker
import tech.fika.monaka.core.Effect as EffectMarker
import tech.fika.monaka.core.LifecycleEvent
import tech.fika.monaka.core.State as StateMarker

import tech.fika.monaka.handler.ActionHandler
import tech.fika.monaka.handler.LifecycleHandler
import tech.fika.monaka.handler.StateChangeHandler
import tech.fika.monaka.handler.StateErrorHandler
import tech.fika.monaka.handler.StateUpdateHandler
import tech.fika.monaka.plugin.Plugin

/**
 * An immutable snapshot of all configuration data built by [tech.fika.monaka.dsl.StateMachineBuilder].
 *
 * Produced by [tech.fika.monaka.dsl.StateMachineBuilder.build] and consumed by the runtime to create a
 * [tech.fika.monaka.core.Store]. Separating configuration from execution allows the
 * same configuration to be inspected, stored, or used to create multiple machines.
 *
 * @param initialState   The state the machine starts in.
 * @param actionHandlers Registered `on<>` handlers keyed by state class → action class.
 * @param enterHandlers  Hooks fired when the machine transitions *into* a state type.
 * @param exitHandlers   Hooks fired when the machine transitions *out of* a state type.
 * @param updateHandlers Hooks fired when the state value changes within the same type.
 * @param lifecycleHandlers Hooks fired for forwarded [LifecycleEvent]s per state class.
 * @param errorHandlers  Recovery hooks fired when a handler or hook throws, keyed by state class.
 * @param plugins        Observers installed in the machine, invoked in registration order.
 */
interface StateMachine<State : StateMarker, Action : ActionMarker, Effect : EffectMarker> {
    val id: String
    val initialState: State
    val actionHandlers: Map<KClass<*>, Map<KClass<*>, ActionHandler<State, Action, Effect>>>
    val enterHandlers: Map<KClass<*>, StateChangeHandler<State, Action, Effect>>
    val exitHandlers: Map<KClass<*>, StateChangeHandler<State, Action, Effect>>
    val updateHandlers: Map<KClass<*>, StateUpdateHandler<State, Action, Effect>>
    val lifecycleHandlers: Map<KClass<*>, Map<LifecycleEvent, LifecycleHandler<State, Action, Effect>>>
    val errorHandlers: Map<KClass<*>, StateErrorHandler<State, Action, Effect>>
    val plugins: List<Plugin<State, Action, Effect>>
}
