package dev.gmvalentino.monaka.dsl

import dev.gmvalentino.monaka.core.LifecycleEvent
import dev.gmvalentino.monaka.handler.ActionHandler
import dev.gmvalentino.monaka.handler.LifecycleHandler
import dev.gmvalentino.monaka.handler.StateChangeHandler
import dev.gmvalentino.monaka.handler.StateErrorHandler
import dev.gmvalentino.monaka.handler.StateUpdateHandler
import kotlin.reflect.KClass
import dev.gmvalentino.monaka.core.Action as ActionMarker
import dev.gmvalentino.monaka.core.Effect as EffectMarker
import dev.gmvalentino.monaka.core.State as StateMarker

/**
 * An immutable snapshot of all configuration data built by [dev.gmvalentino.monaka.dsl.StateMachineBuilder].
 *
 * Produced by [dev.gmvalentino.monaka.dsl.StateMachineBuilder.build] and consumed by the runtime to create a
 * [dev.gmvalentino.monaka.core.Store]. Separating configuration from execution allows the
 * same configuration to be inspected, stored, or used to create multiple machines.
 *
 * @param name           Human-readable name for the machine, used as [dev.gmvalentino.monaka.core.Store.name] on created stores.
 * @param initialState   The state the machine starts in.
 * @param actionHandlers Registered `on<>` handlers keyed by state class → action class.
 * @param enterHandlers  Hooks fired when the machine transitions *into* a state type.
 * @param exitHandlers   Hooks fired when the machine transitions *out of* a state type.
 * @param updateHandlers Hooks fired when the state value changes within the same type.
 * @param lifecycleHandlers Hooks fired for forwarded [LifecycleEvent]s per state class.
 * @param errorHandlers  Recovery hooks fired when a handler or hook throws, keyed by state class.
 */
public interface StateMachine<State : StateMarker, Action : ActionMarker, Effect : EffectMarker> {
    /** Unique identifier for this configuration, used as the [dev.gmvalentino.monaka.core.Store.id] of created stores. */
    public val id: String

    /** Human-readable name for this machine, used as the [dev.gmvalentino.monaka.core.Store.name] of created stores. Defaults to an empty string if not set. */
    public val name: String?

    /**
     * The state the machine starts in before any action is processed, or `null` if the initial
     * state is deferred to the call site.
     */
    public val initialState: State?

    /** Registered `on<>` handlers, keyed by state class then action class. */
    public val actionHandlers: Map<KClass<out State>, Map<KClass<out Action>, ActionHandler<State, Action, Effect>>>

    /** Hooks registered via `onEnter { }`, keyed by state class. */
    public val enterHandlers: Map<KClass<out State>, StateChangeHandler<State, Action, Effect>>

    /** Hooks registered via `onExit { }`, keyed by state class. */
    public val exitHandlers: Map<KClass<out State>, StateChangeHandler<State, Action, Effect>>

    /** Hooks registered via `onUpdate { }`, keyed by state class. */
    public val updateHandlers: Map<KClass<out State>, StateUpdateHandler<State, Action, Effect>>

    /** Hooks registered via `onResume { }` etc., keyed by state class then lifecycle event. */
    public val lifecycleHandlers: Map<KClass<out State>, Map<LifecycleEvent, LifecycleHandler<State, Action, Effect>>>

    /** Recovery hooks registered via `onError { }`, keyed by state class. */
    public val errorHandlers: Map<KClass<out State>, StateErrorHandler<State, Action, Effect>>
}
