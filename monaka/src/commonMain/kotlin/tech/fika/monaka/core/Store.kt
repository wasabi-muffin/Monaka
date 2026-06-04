package tech.fika.monaka.core

import tech.fika.monaka.core.State as StateMarker
import tech.fika.monaka.core.Action as ActionMarker
import tech.fika.monaka.core.Effect as EffectMarker
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
 *         on<MyAction.Start> { transition { MyState.Loading } }
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
interface Store<out State : StateMarker, Action : ActionMarker, out Effect : EffectMarker> {

    val id: String

    /**
     * The current state, exposed as a [StateFlow].
     * Always has a value; the initial emission is the configured initialState.
     */
    val state: StateFlow<State>

    /**
     * Every action dispatched to this machine, exposed as a [SharedFlow] with no replay.
     *
     * Emits in [dispatch] order, before the action is processed. Use this to observe
     * or relay actions to another machine via [tech.fika.monaka.relay.RelayBuilder.action].
     */
    val actions: SharedFlow<Action>

    /**
     * One-shot side effects, exposed as a [SharedFlow] with no replay.
     *
     * Late subscribers will not receive effects that were emitted before they
     * started collecting. Buffer overflow is handled with extraBufferCapacity
     * so that fast producers don't block state processing.
     */
    val effects: SharedFlow<Effect>

    /**
     * Enqueue an [action] for processing.
     *
     * Actions are processed sequentially in the order they are dispatched.
     * This function is non-suspending and safe to call from any context,
     * including the main thread.
     */
    fun dispatch(action: Action)

    /**
     * Cancel the internal processing coroutine.
     *
     * After cancellation, no further actions will be processed and no new
     * state/effect emissions will occur. On Android, prefer tying the
     * state machine's [kotlinx.coroutines.CoroutineScope] to the ViewModel lifecycle instead
     * of calling this manually.
     */
    fun cancel()

    /**
     * Whether this store is still active and processing actions.
     *
     * Returns `false` after [cancel] is called or after the store's owning
     * [kotlinx.coroutines.CoroutineScope] is cancelled externally (e.g. a cleared ViewModel scope).
     * Once inactive, [dispatch], [start], [onLifecycleEvent], and [triggerStateHook] are all silent no-ops.
     */
    val isActive: Boolean get() = true

    /**
     * Start the store by firing the `onEnter` hook for the initial state, if one is registered.
     *
     * Call this once after all observers (state collectors, effect handlers) are attached, so
     * that any state transition or effect emitted by the initial `onEnter` is not missed.
     *
     * Calling [start] more than once is a safe no-op — the hook fires at most once per store
     * instance. Calling [start] after [cancel] is also a no-op.
     *
     * The default implementation is a no-op, so stores that do not use `onEnter` on the
     * initial state do not need to call this.
     */
    fun start(): Unit = Unit

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
    fun onLifecycleEvent(event: LifecycleEvent): Unit = Unit

    /**
     * Fire a state-lifecycle [hook] for the current state directly, without requiring a transition.
     *
     * Enqueued in the same sequential channel as actions and lifecycle events, so it is
     * processed in arrival order. The hook fires the same handler that would run during a
     * real transition — `onEnter`, `onExit`, or `onUpdate` registered for the current state.
     *
     * Primarily intended for testing via `:monaka-test`'s `trigger(StateHook)` DSL.
     */
    fun triggerStateHook(hook: StateHook): Unit = Unit

    /**
     * Register a [handler] to be invoked when this store's scope completes (including cancellation).
     *
     * The handler receives the cancellation cause, or `null` if the scope completed normally.
     * Use this to observe store lifetime without needing to hold a reference to the underlying scope.
     */
    fun invokeOnCompletion(handler: (cause: Throwable?) -> Unit): DisposableHandle
}
