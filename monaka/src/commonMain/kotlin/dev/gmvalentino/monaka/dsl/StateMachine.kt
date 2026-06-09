package dev.gmvalentino.monaka.dsl

import kotlin.reflect.KClass
import dev.gmvalentino.monaka.core.Action as ActionMarker
import dev.gmvalentino.monaka.core.Effect as EffectMarker
import dev.gmvalentino.monaka.core.LifecycleEvent
import dev.gmvalentino.monaka.core.State as StateMarker

import dev.gmvalentino.monaka.handler.ActionHandler
import dev.gmvalentino.monaka.handler.LifecycleHandler
import dev.gmvalentino.monaka.handler.StateChangeHandler
import dev.gmvalentino.monaka.handler.StateErrorHandler
import dev.gmvalentino.monaka.handler.StateUpdateHandler
import dev.gmvalentino.monaka.plugin.Plugin

/**
 * An immutable snapshot of all configuration data built by [dev.gmvalentino.monaka.dsl.StateMachineBuilder].
 *
 * Produced by [dev.gmvalentino.monaka.dsl.StateMachineBuilder.build] and consumed by the runtime to create a
 * [dev.gmvalentino.monaka.core.Store]. Separating configuration from execution allows the
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
public interface StateMachine<State : StateMarker, Action : ActionMarker, Effect : EffectMarker> {
    public val id: String
    public val initialState: State
    public val actionHandlers: Map<KClass<out State>, Map<KClass<out Action>, ActionHandler<State, Action, Effect>>>
    public val enterHandlers: Map<KClass<out State>, StateChangeHandler<State, Action, Effect>>
    public val exitHandlers: Map<KClass<out State>, StateChangeHandler<State, Action, Effect>>
    public val updateHandlers: Map<KClass<out State>, StateUpdateHandler<State, Action, Effect>>
    public val lifecycleHandlers: Map<KClass<out State>, Map<LifecycleEvent, LifecycleHandler<State, Action, Effect>>>
    public val errorHandlers: Map<KClass<out State>, StateErrorHandler<State, Action, Effect>>
    public val plugins: List<Plugin<State, Action, Effect>>
}
