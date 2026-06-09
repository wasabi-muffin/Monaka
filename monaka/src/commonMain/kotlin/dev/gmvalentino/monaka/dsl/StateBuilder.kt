package dev.gmvalentino.monaka.dsl

import kotlin.reflect.KClass
import dev.gmvalentino.monaka.core.Action as ActionMarker
import dev.gmvalentino.monaka.core.Effect as EffectMarker
import dev.gmvalentino.monaka.core.LifecycleEvent
import dev.gmvalentino.monaka.core.State as StateMarker
import dev.gmvalentino.monaka.scopes.ActionScope
import dev.gmvalentino.monaka.scopes.ErrorScope
import dev.gmvalentino.monaka.scopes.HandlerScope
import dev.gmvalentino.monaka.scopes.LifecycleScope
import dev.gmvalentino.monaka.scopes.StateChangeScope
import dev.gmvalentino.monaka.scopes.StateUpdateScope
import dev.gmvalentino.monaka.handler.ActionHandler
import dev.gmvalentino.monaka.handler.LifecycleHandler
import dev.gmvalentino.monaka.handler.StateChangeHandler
import dev.gmvalentino.monaka.handler.StateErrorHandler
import dev.gmvalentino.monaka.handler.StateUpdateHandler

/**
 * DSL scope for registering action handlers and lifecycle hooks for a specific state type [SubState].
 *
 * @param State    The parent state type of the enclosing state machine.
 * @param SubState The concrete state subtype this builder handles (SubState : State).
 * @param Action   The action type of the enclosing state machine.
 * @param Effect   The effect type of the enclosing state machine.
 *
 * ### Action handlers
 * Registered via [on]. Handlers receive:
 * - An implicit [HandlerScope] receiver (`this`) — gives access to [HandlerScope.dispatch]
 *   and [HandlerScope.task].
 * - `state: SubState` — the current state typed to the concrete subtype; no cast needed.
 * - `action: ActionType` — the dispatched action typed to the registered subtype.
 *
 * ### State lifecycle hooks
 * - [onEnter] — fires when the machine transitions **into** [SubState] from a different state type.
 * - [onExit]  — fires when the machine transitions **out of** [SubState] to a different state type.
 * - [onUpdate] — fires when the machine stays in [SubState] but the state **value** changes.
 *
 * Hooks have [HandlerScope] as their implicit receiver. Use [HandlerScope.transition] to
 * drive a state change, [HandlerScope.sideEffect] to emit effects, [HandlerScope.dispatch]
 * to enqueue a follow-up action, or [HandlerScope.task] to fire off async work. Doing
 * nothing is a silent no-op.
 *
 * ### Application lifecycle hooks
 * - [onResume], [onPause], [onStart], [onStop], [onCreate], [onDestroy] — fire when the
 *   corresponding [LifecycleEvent] is forwarded via [dev.gmvalentino.monaka.core.Store.onLifecycleEvent]
 *   and the machine is currently in [SubState].
 */
@MonakaDsl
public class StateBuilder<State : StateMarker, SubState : State, Action : ActionMarker, Effect : EffectMarker> {

    @PublishedApi
    internal val handlers: LinkedHashMap<KClass<out Action>, ActionHandler<State, Action, Effect>> = LinkedHashMap()

    @PublishedApi
    internal var enterHandler: StateChangeHandler<State, Action, Effect>? = null

    @PublishedApi
    internal var exitHandler: StateChangeHandler<State, Action, Effect>? = null

    @PublishedApi
    internal var updateHandler: StateUpdateHandler<State, Action, Effect>? = null

    @PublishedApi
    internal val lifecycleHandlers: LinkedHashMap<LifecycleEvent, LifecycleHandler<State, Action, Effect>> = LinkedHashMap()

    @PublishedApi
    internal var errorHandler: StateErrorHandler<State, Action, Effect>? = null

    // ── Action handlers ───────────────────────────────────────────────────────

    /**
     * Register a suspend handler for action type [ActionType] when the machine is in state [SubState].
     *
     * The lambda's implicit receiver is [ActionScope], which exposes [HandlerScope.dispatch]
     * and [HandlerScope.task]. Handlers that don't need
     * these — i.e., pure state transitions — simply ignore the receiver.
     *
     * If [ActionType] is registered more than once, the last registration wins.
     */
    public inline fun <reified ActionType : Action> on(
        noinline handler: suspend ActionScope<State, Action, Effect, SubState, ActionType>.() -> Unit,
    ) {
        handlers[ActionType::class] = {
            @Suppress("UNCHECKED_CAST")
            (this as ActionScope<State, Action, Effect, SubState, ActionType>).handler()
        }
    }

    // ── State lifecycle hooks ─────────────────────────────────────────────────

    /**
     * Register a hook that fires whenever the machine transitions **into** [SubState]
     * from a different state type.
     *
     * Use this to start loading data, begin timers, or emit effects on state entry.
     *
     * The block returns [Unit]. Use [HandlerScope.transition] to drive an immediate
     * state change, [HandlerScope.sideEffect] to emit effects, [HandlerScope.dispatch]
     * to enqueue a follow-up action, or do nothing for a silent no-op.
     *
     * ```kotlin
     * state<MyState.Loading> {
     *     onEnter {
     *         val data = repository.load(state.id)
     *         transition(MyState.Loaded(data))
     *     }
     * }
     * ```
     *
     * Note: [onEnter] does **not** fire for the initial state — only on transitions.
     */
    public fun onEnter(block: suspend StateChangeScope<State, Action, Effect, SubState>.() -> Unit) {
        enterHandler = {
            @Suppress("UNCHECKED_CAST")
            (this as StateChangeScope<State, Action, Effect, SubState>).block()
        }
    }

    /**
     * Register a hook that fires whenever the machine transitions **out of** [SubState]
     * to a different state type.
     *
     * Use this for cleanup — cancelling jobs, releasing resources, or flushing pending work.
     *
     * ```kotlin
     * state<MyState.Active> {
     *     onExit {
     *         cancel("poll")
     *     }
     * }
     * ```
     */
    public fun onExit(block: suspend StateChangeScope<State, Action, Effect, SubState>.() -> Unit) {
        exitHandler = {
            @Suppress("UNCHECKED_CAST")
            (this as StateChangeScope<State, Action, Effect, SubState>).block()
        }
    }

    /**
     * Register a hook that fires when the state **value** changes but the state **type**
     * stays [SubState] (i.e. the machine stays in the same substate class).
     *
     * ```kotlin
     * state<MyState.Active> {
     *     onUpdate { old, new ->
     *         if (old.query != new.query) task { analytics.track(new.query) }
     *     }
     * }
     * ```
     */
    public fun onUpdate(block: suspend StateUpdateScope<State, Action, Effect, SubState>.() -> Unit) {
        updateHandler = {
            @Suppress("UNCHECKED_CAST")
            (this as StateUpdateScope<State, Action, Effect, SubState>).block()
        }
    }

    // ── Application lifecycle hooks ───────────────────────────────────────────

    /**
     * Register a hook for an arbitrary [LifecycleEvent].
     *
     * Prefer the named convenience functions ([onResume], [onPause], etc.) when possible.
     */
    public fun onLifecycle(
        lifecycle: LifecycleEvent,
        block: suspend LifecycleScope<State, Action, Effect, SubState>.() -> Unit,
    ) {
        lifecycleHandlers[lifecycle] = {
            @Suppress("UNCHECKED_CAST")
            (this as LifecycleScope<State, Action, Effect, SubState>).block()
        }
    }

    /**
     * Hook fired when [LifecycleEvent.OnCreate] is forwarded to the machine while
     * the machine is in [SubState].
     */
    public fun onCreate(block: suspend LifecycleScope<State, Action, Effect, SubState>.() -> Unit): Unit =
        onLifecycle(lifecycle = LifecycleEvent.OnCreate, block)

    /**
     * Hook fired when [LifecycleEvent.OnStart] is forwarded to the machine while
     * the machine is in [SubState].
     */
    public fun onStart(block: suspend LifecycleScope<State, Action, Effect, SubState>.() -> Unit): Unit =
        onLifecycle(lifecycle = LifecycleEvent.OnStart, block = block)

    /**
     * Hook fired when [LifecycleEvent.OnResume] is forwarded to the machine while
     * the machine is in [SubState].
     *
     * ```kotlin
     * state<FeedState.Active> {
     *     onResume {
     *         // restart polling when the app comes back to foreground
     *         dispatch(FeedAction.GoLive)
     *     }
     * }
     * ```
     */
    public fun onResume(block: suspend LifecycleScope<State, Action, Effect, SubState>.() -> Unit): Unit =
        onLifecycle(lifecycle = LifecycleEvent.OnResume, block = block)

    /**
     * Hook fired when [LifecycleEvent.OnPause] is forwarded to the machine while
     * the machine is in [SubState].
     */
    public fun onPause(block: suspend LifecycleScope<State, Action, Effect, SubState>.() -> Unit): Unit =
        onLifecycle(lifecycle = LifecycleEvent.OnPause, block = block)

    /**
     * Hook fired when [LifecycleEvent.OnStop] is forwarded to the machine while
     * the machine is in [SubState].
     */
    public fun onStop(block: suspend LifecycleScope<State, Action, Effect, SubState>.() -> Unit): Unit =
        onLifecycle(lifecycle = LifecycleEvent.OnStop, block = block)

    /**
     * Hook fired when [LifecycleEvent.OnDestroy] is forwarded to the machine while
     * the machine is in [SubState].
     */
    public fun onDestroy(block: suspend LifecycleScope<State, Action, Effect, SubState>.() -> Unit): Unit =
        onLifecycle(lifecycle = LifecycleEvent.OnDestroy, block = block)

    // ── Error recovery hook ───────────────────────────────────────────────────

    /**
     * Register a recovery hook that fires when any handler or hook throws an exception
     * while the machine is in [SubState].
     *
     * The raw [Throwable] is passed directly to this block as [ErrorScope.error]. The
     * handler origin is available as [ErrorScope.handlerType].
     *
     * The hook records its outcome with the same verbs as any other handler — use
     * [HandlerScope.transition] to move to an error state, [HandlerScope.sideEffect] to
     * emit an effect, or do nothing to silently absorb the error.
     *
     * If the recovery hook itself throws, the exception is forwarded to plugins via
     * [dev.gmvalentino.monaka.plugin.Plugin.onError] and no further recovery is attempted.
     *
     * ```kotlin
     * state<MyState.Loading> {
     *     onEnter {
     *         val data = repository.load()
     *         transition(MyState.Loaded(data))
     *     }
     *     onError {
     *         transition(MyState.Error(error.message ?: "Unknown error"))
     *     }
     * }
     * ```
     */
    public fun onError(block: suspend ErrorScope<State, Action, Effect, SubState>.() -> Unit) {
        errorHandler = {
            @Suppress("UNCHECKED_CAST")
            (this as ErrorScope<State, Action, Effect, SubState>).block()
        }
    }
}
