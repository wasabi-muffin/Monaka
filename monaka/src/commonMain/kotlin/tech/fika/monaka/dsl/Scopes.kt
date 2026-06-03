package tech.fika.monaka.dsl

import kotlinx.coroutines.CoroutineScope
import tech.fika.monaka.core.Action as ActionMarker
import tech.fika.monaka.core.Effect as EffectMarker
import tech.fika.monaka.core.State as StateMarker
import tech.fika.monaka.handler.HandlerResult
import tech.fika.monaka.handler.HandlerType
import tech.fika.monaka.runtime.JobRegistry

/**
 * Internal: snapshot the accumulated [HandlerScope] mutations into a [HandlerResult].
 *
 * Called by the runtime after each handler lambda returns. Order of precedence:
 * 1. If [HandlerScope.rejected] was set → [HandlerResult.Rejected].
 * 2. Else if [HandlerScope.pendingState] was set → [HandlerResult.Transition] carrying
 *    the new state and all accumulated [HandlerScope.pendingEffects] in call order.
 * 3. Else if any side effects were emitted → [HandlerResult.SideEffect].
 * 4. Otherwise → [HandlerResult.Done].
 */
internal fun <State : StateMarker, Action : ActionMarker, Effect : EffectMarker> HandlerScope<State, Action, Effect, *>.consumeResult(): HandlerResult<State, Effect> =
    when {
        rejected -> HandlerResult.Rejected
        pendingState != null -> HandlerResult.Transition(state = pendingState!!, effects = pendingEffects.toList())
        pendingEffects.isNotEmpty() -> HandlerResult.SideEffect(effects = pendingEffects.toList())
        else -> HandlerResult.Done
    }

/**
 * Implicit receiver available inside every `on<>` handler lambda.
 *
 * It exposes the machine's [CoroutineScope] and a [dispatch] function so that
 * handlers can call UseCases/Repositories using two complementary patterns:
 *
 * ### Pattern 1 — Inline suspend (blocking)
 * The handler suspends until the UseCase returns. The action queue is paused
 * for that duration, so no other actions are processed concurrently.
 * Use this when you want strict sequential processing (e.g. one request at a time).
 *
 * ```kotlin
 * on<LoginAction.Submit> {
 *     val result = loginUseCase.execute(state.username, state.password) // suspend
 *     when (result) {
 *         is Result.Success -> transition { LoginState.Authenticated(result.user) }
 *         is Result.Failure -> transition { LoginState.Error(result.message) }
 *     }
 * }
 * ```
 *
 * ### Pattern 2 — Keyed jobs (cancellable by name)
 * Pass a string key to `task` to have the machine automatically cancel the previous
 * job with the same key before starting a new one. Use this for debounce, polling loops,
 * and any "replace previous" semantics — no `Job` variable needed outside the machine.
 *
 * Pass `autoCancel = true` to have the machine cancel the task when the state type changes,
 * so background work tied to a state does not outlive that state.
 *
 * ```kotlin
 * on<QueryChanged> {
 *     task("search") {                // cancels the previous "search" job, if any
 *         delay(300)
 *         dispatch(SearchCompleted(feedRepository.search(action.query)))
 *     }
 *     transition { state.copy(isLoading = true) }
 * }
 *
 * on<PauseLive> {
 *     cancel("poll")                  // cancel the polling loop started by GoLive
 *     transition { state.copy(isLive = false) }
 * }
 * ```
 *
 * @param machineScope A [CoroutineScope] tied to the state machine's lifetime.
 *                     Coroutines launched here are canceled when the machine is canceled.
 * @param dispatch     The underlying dispatch function of the enclosing machine.
 * @param jobRegistry  The machine's [JobRegistry] for keyed job management.
 */
/**
 * Implicit receiver shared by every handler lambda (action, lifecycle, state-change, and error).
 *
 * Handlers are **statements**, not expressions: call [transition], [sideEffect], or [reject] to
 * record what the runtime should do after the lambda returns. The lambda itself returns [Unit].
 *
 * ### Result resolution
 * After the lambda finishes, the runtime resolves the recorded state as follows:
 * 1. [reject] was called → the action is treated as rejected; plugins notified via `onInvalid`.
 * 2. [transition] was called → the recorded state becomes the new state, followed by all
 *    accumulated [sideEffect] emissions in call order.
 * 3. Only [sideEffect] was called → effects emit; state is unchanged.
 * 4. Nothing was called → silent no-op.
 *
 * ### First-write-wins for [transition]
 * The first [transition] call records the new state. Subsequent calls are silent no-ops and
 * their blocks are **not evaluated**. This enables a "fallback" pattern:
 *
 * ```kotlin
 * on<Refresh> {
 *     if (state.isStale) transition { Refreshing }
 *     transition { Active }   // fallback when not stale
 * }
 * ```
 *
 * ### Terminal [reject]
 * Once [reject] is called, all subsequent [transition], [sideEffect], [dispatch], [task],
 * and [cancel] calls become no-ops in the same handler invocation. The runtime emits a
 * rejected result regardless of what was recorded before.
 */
@MonakaDsl
abstract class HandlerScope<State : StateMarker, Action : ActionMarker, Effect : EffectMarker, SubState : State> internal constructor(
    open val machineScope: CoroutineScope,
    open val state: SubState,
    private val internalDispatch: (Action) -> Unit,
    private val jobRegistry: JobRegistry,
) {

    @PublishedApi
    internal var pendingState: State? = null
    @PublishedApi
    internal val pendingEffects: MutableList<Effect> = mutableListOf()
    @PublishedApi
    internal var rejected: Boolean = false

    // ── Result builders ───────────────────────────────────────────────────────

    /**
     * Record the new state produced by [block]. First call wins — subsequent calls in the same
     * handler are no-ops and [block] is not evaluated.
     *
     * ```kotlin
     * on<MyAction.Load> {
     *     transition { MyState.Loading(action.id) }
     * }
     * ```
     */
    fun <S : State> transition(block: () -> S) {
        if (rejected || pendingState != null) return
        pendingState = block()
    }

    /**
     * Convenience overload: record a transition and emit [effects] in one call.
     * Equivalent to `transition(block); sideEffect(*effects)`.
     *
     * ```kotlin
     * on<MyAction.Save> {
     *     transition(MyEffect.Saved) { MyState.Idle }
     * }
     * ```
     */
    fun <S : State> transition(vararg effects: Effect, block: () -> S) {
        transition(block)
        sideEffect(effects = effects)
    }

    /**
     * Emit [effects] in the order they appear. Multiple calls accumulate.
     *
     * If [reject] has already been called in this handler, this call is a no-op.
     *
     * ```kotlin
     * on<MyAction.Submit> {
     *     transition { MyState.Submitting }
     *     sideEffect(MyEffect.Analytics("submit_started"))
     *     if (state.shouldNotify) sideEffect(MyEffect.Notify)
     * }
     * ```
     */
    fun sideEffect(vararg effects: Effect) {
        if (rejected) return
        pendingEffects += effects
    }

    /**
     * Mark the action as rejected. Plugins are notified via
     * [tech.fika.monaka.plugin.Plugin.onRejected]; no state change or effect emission occurs.
     *
     * Terminal: all subsequent [transition], [sideEffect], [dispatch], [task], and [cancel]
     * calls in the same handler become no-ops.
     *
     * ```kotlin
     * on<MyAction.Submit> {
     *     if (!state.isValid) { reject(); return@on }
     *     transition { MyState.Submitting }
     * }
     * ```
     */
    fun reject() {
        rejected = true
    }

    // ── Async helpers ─────────────────────────────────────────────────────────

    /**
     * Enqueue [action] to be processed by this state machine.
     *
     * The action is added to the end of the queue and will be processed after the current
     * handler returns (and any other queued actions ahead of it). Safe to call from any
     * coroutine; does not suspend. No-op if [reject] has already been called.
     */
    fun dispatch(action: Action) {
        if (rejected) return
        internalDispatch(action)
    }

    /**
     * Launch a fire-and-forget coroutine in [coroutineScope] (defaults to [machineScope]).
     *
     * When [autoCancel] is true, the job is canceled on the next state-type change
     * (before the corresponding `onExit` hook fires). No-op if [reject] has already been called.
     */
    fun task(
        autoCancel: Boolean = false,
        coroutineScope: CoroutineScope = machineScope,
        block: suspend CoroutineScope.() -> Unit,
    ) {
        if (rejected) return
        jobRegistry.launch(scope = coroutineScope, autoCancel = autoCancel, block = block)
    }

    /**
     * Cancel any job previously registered under [key], launch a new keyed coroutine in
     * [coroutineScope] (defaults to [machineScope]), and register it under [key].
     *
     * Use for debounce and "latest wins" patterns. When [autoCancel] is true, the job is
     * additionally canceled (and its key unregistered) on the next state-type change.
     * No-op if [reject] has already been called.
     */
    fun task(
        key: String,
        autoCancel: Boolean = false,
        coroutineScope: CoroutineScope = machineScope,
        block: suspend CoroutineScope.() -> Unit,
    ) {
        if (rejected) return
        jobRegistry.launch(scope = coroutineScope, key = key, autoCancel = autoCancel, block = block)
    }


    /**
     * Cancel the job registered under [key], if any, and remove it from the registry.
     * No-op if [reject] has already been called.
     */
    fun cancel(key: String) {
        if (rejected) return
        jobRegistry.cancel(key)
    }
}

/**
 * Implicit receiver available inside `onError` hooks.
 *
 * Extends [HandlerScope] with the mapped [error] and the [handlerType] that was
 * being processed when the exception was thrown.
 *
 * The handler can inspect both to decide how to recover — e.g. transition to an
 * error state, emit an effect, or re-dispatch a retry action.
 *
 * @param machineScope A [CoroutineScope] tied to the state machine's lifetime.
 * @param state        The current state at the time the error was thrown.
 * @param error        The raw [Throwable] thrown by the handler or hook.
 * @param handlerType  Which handler origin threw the exception.
 * @param dispatch     The underlying dispatch function of the enclosing machine.
 * @param jobRegistry  The machine's [JobRegistry] for keyed job management.
 */
@MonakaDsl
class ErrorScope<State : StateMarker, Action : ActionMarker, Effect : EffectMarker, SubState : State> internal constructor(
    override val machineScope: CoroutineScope,
    override val state: SubState,
    val error: Throwable,
    val handlerType: HandlerType<Action>,
    private val dispatch: (Action) -> Unit,
    private val jobRegistry: JobRegistry,
) : HandlerScope<State, Action, Effect, SubState>(
    machineScope = machineScope,
    state = state,
    internalDispatch = dispatch,
    jobRegistry = jobRegistry,
)

/**
 * Implicit receiver available inside application lifecycle hooks
 * (`onResume`, `onPause`, `onStart`, `onStop`, `onCreate`, `onDestroy`).
 *
 * Identical to [HandlerScope] except that it has no `action` property — lifecycle events
 * do not originate from a dispatched action.
 *
 * @param machineScope A [CoroutineScope] tied to the state machine's lifetime.
 * @param dispatch     The underlying dispatch function of the enclosing machine.
 * @param jobRegistry  The machine's [JobRegistry] for keyed job management.
 */
@MonakaDsl
class LifecycleScope<State : StateMarker, Action : ActionMarker, Effect : EffectMarker, SubState : State> internal constructor(
    override val machineScope: CoroutineScope,
    override val state: SubState,
    private val dispatch: (Action) -> Unit,
    private val jobRegistry: JobRegistry,
) : HandlerScope<State, Action, Effect, SubState>(
    state = state,
    machineScope = machineScope,
    internalDispatch = dispatch,
    jobRegistry = jobRegistry,
)

/**
 * Implicit receiver available inside `on<ActionType>` action handlers.
 *
 * Extends [HandlerScope] with typed [state] and [action] properties so that handlers
 * receive the current state and dispatched action without casting.
 *
 * @param machineScope A [CoroutineScope] tied to the state machine's lifetime.
 * @param dispatch     The underlying dispatch function of the enclosing machine.
 * @param jobRegistry  The machine's [JobRegistry] for keyed job management.
 */
@MonakaDsl
class ActionScope<State : StateMarker, Action : ActionMarker, Effect : EffectMarker, SubState : State, ActionType : Action> internal constructor(
    override val machineScope: CoroutineScope,
    override val state: SubState,
    val action: ActionType,
    private val dispatch: (Action) -> Unit,
    private val jobRegistry: JobRegistry,
) : HandlerScope<State, Action, Effect, SubState>(
    machineScope = machineScope,
    state = state,
    internalDispatch = dispatch,
    jobRegistry = jobRegistry,
)

/**
 * Implicit receiver available inside state change hooks (`onEnter`, `onExit`).
 *
 * Identical to [HandlerScope] except that it has no `action` property — state change
 * hooks do not originate from a dispatched action.
 *
 * @param machineScope A [CoroutineScope] tied to the state machine's lifetime.
 * @param dispatch     The underlying dispatch function of the enclosing machine.
 * @param jobRegistry  The machine's [JobRegistry] for keyed job management.
 */
@MonakaDsl
class StateChangeScope<State : StateMarker, Action : ActionMarker, Effect : EffectMarker, SubState : State> internal constructor(
    override val machineScope: CoroutineScope,
    override val state: SubState,
    private val dispatch: (Action) -> Unit,
    private val jobRegistry: JobRegistry,
) : HandlerScope<State, Action, Effect, SubState>(
    machineScope = machineScope,
    state = state,
    internalDispatch = dispatch,
    jobRegistry = jobRegistry,
)

/**
 * Implicit receiver available inside `onUpdate` hooks.
 *
 * Provides both [fromState] and [toState] so that handlers can react to specific
 * field changes within the same state type.
 *
 * @param machineScope A [CoroutineScope] tied to the state machine's lifetime.
 * @param dispatch     The underlying dispatch function of the enclosing machine.
 * @param jobRegistry  The machine's [JobRegistry] for keyed job management.
 */
@MonakaDsl
class StateUpdateScope<State : StateMarker, Action : ActionMarker, Effect : EffectMarker, SubState : State> internal constructor(
    override val machineScope: CoroutineScope,
    override val state: SubState,
    val fromState: SubState,
    private val dispatch: (Action) -> Unit,
    private val jobRegistry: JobRegistry,
) : HandlerScope<State, Action, Effect, SubState>(
    machineScope = machineScope,
    state = state,
    internalDispatch = dispatch,
    jobRegistry = jobRegistry,
)
