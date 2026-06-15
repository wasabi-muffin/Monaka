package dev.gmvalentino.monaka.core

import dev.gmvalentino.monaka.core.State as StateMarker
import dev.gmvalentino.monaka.core.Action as ActionMarker
import dev.gmvalentino.monaka.core.Effect as EffectMarker
import dev.gmvalentino.monaka.plugin.Plugin
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The core StateMachine contract.
 *
 * @param State  State type. Must implement [StateMarker].
 * @param Action Action type. Must implement [ActionMarker]. Represents intents/events.
 * @param Effect Effect type. Must implement [EffectMarker]. One-shot side effects.
 *
 * Usage pattern:
 * ```kotlin
 * val store = store<MyState, MyAction, MyEffect>(scope) {
 *     initialState(MyState.Idle)
 *     state<MyState.Idle> {
 *         on<MyAction.Start> { transition(MyState.Loading) }
 *     }
 * }
 *
 * // Observe state
 * machine.state.collect { render(it) }
 *
 * // Observe effects
 * machine.effects.collect { handle(it) }
 *
 * // Send actions
 * machine.dispatch(MyAction.Start)
 * ```
 */
public interface Store<State : StateMarker, Action : ActionMarker, out Effect : EffectMarker> {

    /** Unique identifier for this store instance. Auto-generated as a UUID by default. */
    public val id: String

    /** Human-readable name for this store, set via [dev.gmvalentino.monaka.dsl.StateMachineBuilder.name]. Defaults to an empty string if not set. */
    public val name: String

    /**
     * The current state, exposed as a [StateFlow].
     * Always has a value; the initial emission is the configured initialState.
     */
    public val state: StateFlow<State>

    /**
     * Every action dispatched to this machine, exposed as a [SharedFlow] with no replay.
     *
     * Emits in [dispatch] order, before the action is processed. Use this to observe
     * or relay actions to another machine via [dev.gmvalentino.monaka.relay.RelayBuilder.action].
     */
    public val actions: SharedFlow<Action>

    /**
     * One-shot side effects, exposed as a [SharedFlow] with no replay.
     *
     * Late subscribers will not receive effects that were emitted before they started collecting.
     *
     * **Subscription ordering:** collecting [state] implicitly calls [start], which fires the
     * initial `onEnter` hook. Any effects emitted by that hook are lost if no subscriber has
     * attached to [effects] yet. Always subscribe to [effects] **before** subscribing to [state],
     * or call [start] explicitly only after all subscribers are attached:
     *
     * ```kotlin
     * // Safe — effects subscriber is attached before start() triggers onEnter
     * launch { store.effects.collect { handle(it) } }
     * launch { store.state.collect { render(it) } }
     *
     * // Also safe — explicit start() after both collectors are ready
     * launch { store.effects.collect { handle(it) } }
     * launch { store.state.collect { render(it) } }
     * store.start()
     * ```
     *
     * **Backpressure:** if no subscriber is consuming effects and the internal buffer fills up,
     * the processing coroutine will suspend until space is available, pausing all state
     * transitions. Ensure at least one subscriber consumes effects promptly, or increase
     * `extraBufferCapacity` at store construction time.
     */
    public val effects: SharedFlow<Effect>

    /**
     * Whether this store is still active and processing actions.
     *
     * Returns `false` after [stop] is called or after the store's owning
     * [kotlinx.coroutines.CoroutineScope] is canceled externally (e.g. a cleared ViewModel scope).
     * Once inactive, [dispatch], [start], [onLifecycleEvent], and [triggerStateHook] are all silent no-ops.
     */
    public val isActive: Boolean

    /**
     * Attach [plugin] to this store after construction.
     *
     * The plugin begins receiving events from the next processed action or hook onward —
     * it does not receive events that occurred before this call. Plugins are invoked in
     * installation order; plugins added via this method fire after those supplied at
     * construction time.
     *
     * Primarily intended for [dev.gmvalentino.monaka.runtime.StoreRegistry] global plugin
     * support. Must be called from the same thread that owns the store's [kotlinx.coroutines.CoroutineScope].
     */
    public fun install(plugin: Plugin)

    /**
     * Start the store by firing the `onEnter` hook for the initial state, if one is registered.
     *
     * Call this once after all observers (state collectors, effect handlers) are attached, so
     * that any state transition or effect emitted by the initial `onEnter` is not missed.
     *
     * Calling [start] more than once is a safe no-op — the hook fires at most once per store
     * instance. Calling [start] after [stop] is also a no-op.
     *
     * The default implementation is a no-op, so stores that do not use `onEnter` on the
     * initial state do not need to call this.
     */
    public fun start(): Unit = Unit

    /**
     * Stop the store permanently.
     *
     * Cancels the internal processing coroutine and all running keyed jobs. Closes the trigger
     * channel. No further actions will be processed and no new state/effect emissions will occur.
     *
     * **Important:** [stop] does **not** cancel the owning [kotlinx.coroutines.CoroutineScope],
     * so callbacks registered via [invokeOnCompletion] do **not** fire when [stop] is called
     * directly. Those callbacks are attached to the scope's job and only fire on scope
     * cancellation. If you stop a store early and it is registered in a `StoreRegistry`, call
     * `unregister` manually — otherwise the registry retains a stale reference.
     *
     * On Android, prefer tying the state machine's [kotlinx.coroutines.CoroutineScope] to the
     * ViewModel lifecycle instead of calling this manually — the store stops automatically when
     * the scope is canceled, and [invokeOnCompletion] fires as expected.
     */
    public fun stop()

    /**
     * Enqueue an [action] for processing.
     *
     * Actions are processed sequentially in the order they are dispatched.
     * This function is non-suspending and safe to call from any context,
     * including the main thread.
     */
    public fun dispatch(action: Action)

    /**
     * Forward an application [LifecycleEvent] into the machine.
     *
     * The event is enqueued in the same sequential channel as actions, so it is
     * processed in arrival order relative to any pending actions. State-scoped hooks
     * registered via `onResume { }`, `onPause { }`, etc. in the DSL will fire if the
     * machine is currently in a matching state (exact match first, then ancestor scan).
     *
     * The default implementation is a no-op, so stores that do not use lifecycle hooks
     * do not need to override this.
     */
    public fun onLifecycleEvent(event: LifecycleEvent): Unit = Unit

    /**
     * Register a [handler] to be invoked when this store's owning [kotlinx.coroutines.CoroutineScope]
     * is canceled.
     *
     * The handler receives the cancellation cause, or `null` if the scope completed normally.
     * Use this to observe store lifetime without needing to hold a reference to the underlying scope.
     *
     * **Important:** this callback fires on **scope cancellation only** — it does **not** fire
     * when [stop] is called directly. [stop] cancels the internal processing coroutine but leaves
     * the owning scope intact. If you need cleanup in both cases, either cancel the scope instead
     * of calling [stop], or unregister manually after [stop].
     */
    public fun invokeOnCompletion(handler: (cause: Throwable?) -> Unit): DisposableHandle

    /**
     * Fire a state-lifecycle [hook] for the current state directly, without requiring a transition.
     *
     * Enqueued in the same sequential channel as actions and lifecycle events, so it is
     * processed in arrival order. The hook fires the same handler that would run during a
     * real transition — `onEnter`, `onExit`, or `onUpdate` registered for the current state.
     *
     * Primarily intended for testing via `:monaka-test`'s `trigger(StateHook)` DSL.
     */
    @InternalMonakaApi
    public fun triggerStateHook(hook: StateHook<State>): Unit = Unit
}
