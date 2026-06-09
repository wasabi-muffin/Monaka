package dev.gmvalentino.monaka.runtime

import kotlin.reflect.KClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import dev.gmvalentino.monaka.core.Action as ActionMarker
import dev.gmvalentino.monaka.core.Effect as EffectMarker
import dev.gmvalentino.monaka.core.LifecycleEvent
import dev.gmvalentino.monaka.core.DEFAULT_BUFFER_CAPACITY
import dev.gmvalentino.monaka.core.InternalMonakaApi
import dev.gmvalentino.monaka.core.State as StateMarker
import dev.gmvalentino.monaka.core.StateHook
import dev.gmvalentino.monaka.core.Store
import dev.gmvalentino.monaka.handler.ActionHandler
import dev.gmvalentino.monaka.handler.Handler
import dev.gmvalentino.monaka.handler.HandlerResult
import dev.gmvalentino.monaka.handler.HandlerType
import dev.gmvalentino.monaka.handler.LifecycleHandler
import dev.gmvalentino.monaka.handler.StateChangeHandler
import dev.gmvalentino.monaka.handler.StateErrorHandler
import dev.gmvalentino.monaka.handler.StateUpdateHandler
import dev.gmvalentino.monaka.plugin.Plugin
import dev.gmvalentino.monaka.scopes.ActionScope
import dev.gmvalentino.monaka.scopes.ErrorScope
import dev.gmvalentino.monaka.scopes.HandlerScope
import dev.gmvalentino.monaka.scopes.LifecycleScope
import dev.gmvalentino.monaka.scopes.StateChangeScope
import dev.gmvalentino.monaka.scopes.StateUpdateScope

/**
 * Default runtime implementation of [Store].
 *
 * ### Concurrency model
 * - A single [Channel.UNLIMITED] channel receives both dispatched actions and lifecycle
 *   events, wrapped in [Trigger] so they are processed **sequentially** in arrival order.
 * - A single `processingJob` coroutine consumes them, guaranteeing deterministic,
 *   race-free state transitions.
 * - [MutableStateFlow] and [MutableSharedFlow] are coroutine-safe.
 * - A [HandlerScope] created per processed item exposes [machineScope] (a sibling
 *   of `processingJob`) and [dispatch] so handlers can do fire-and-dispatch async work.
 *
 * ### Keyed job lifecycle
 * All tasks (`task(...) { }`) are tracked in [jobRegistry]. Keyed jobs must be
 * explicitly canceled via `cancel(key)` in `onExit` hooks or action handlers, unless
 * they were started with `autoCancel = true`, in which case the runtime cancels them
 * automatically when the state type changes (just before firing `onExit`).
 *
 * ### State lifecycle hooks
 * After each successful transition, the runtime fires:
 * - [exitHandlers]  for the old state if the state type changed.
 * - [enterHandlers] for the new state if the state type changed.
 * - [updateHandlers] for the current state if the type stayed the same but value changed.
 *
 * ### Application lifecycle hooks
 * Forwarded via [onLifecycleEvent]; enqueued in the same channel as actions and
 * dispatched to [lifecycleHandlers] for the current state.
 *
 * ### Error handling
 * Handler and hook exceptions are caught and discarded — the state remains unchanged.
 * Handler errors are forwarded to plugins via [Plugin.onError].
 */
@OptIn(InternalMonakaApi::class)
internal class DefaultStore<State : StateMarker, Action : ActionMarker, Effect : EffectMarker>(
    override val id: String,
    initialState: State,
    private val actionHandlers: Map<KClass<out State>, Map<KClass<out Action>, ActionHandler<State, Action, Effect>>>,
    private val enterHandlers: Map<KClass<out State>, StateChangeHandler<State, Action, Effect>>,
    private val exitHandlers: Map<KClass<out State>, StateChangeHandler<State, Action, Effect>>,
    private val updateHandlers: Map<KClass<out State>, StateUpdateHandler<State, Action, Effect>>,
    private val lifecycleHandlers: Map<KClass<out State>, Map<LifecycleEvent, LifecycleHandler<State, Action, Effect>>>,
    private val errorHandlers: Map<KClass<out State>, StateErrorHandler<State, Action, Effect>>,
    private val plugins: List<Plugin<State, Action, Effect>>,
    private val machineScope: CoroutineScope = defaultCoroutineScope(),
    private val extraBufferCapacity: Int = DEFAULT_BUFFER_CAPACITY,
) : Store<State, Action, Effect> {
    private enum class Phase { Idle, Running, Cancelled }

    private var phase = Phase.Idle

    private val _state = MutableStateFlow(initialState)
    private val _actions = MutableSharedFlow<Action>(extraBufferCapacity = extraBufferCapacity)
    private val _effects = MutableSharedFlow<Effect>(extraBufferCapacity = extraBufferCapacity)

    private val triggers = Channel<Trigger<State, Action>>(Channel.UNLIMITED)
    private val currentState: State get() = _state.value

    private val ancestorCache = HashMap<KClass<out State>, List<KClass<out State>>>()
    private val registeredStates: Set<KClass<out State>> = buildSet {
        addAll(actionHandlers.keys)
        addAll(enterHandlers.keys)
        addAll(exitHandlers.keys)
        addAll(updateHandlers.keys)
        addAll(lifecycleHandlers.keys)
        addAll(errorHandlers.keys)
    }

    private val jobRegistry = JobRegistry()
    private val processingJob = machineScope.launch {
        for (trigger in triggers) {
            when (trigger) {
                is Trigger.Action -> processAction(action = trigger.action)
                is Trigger.Lifecycle -> processLifecycleEvent(event = trigger.event)
                is Trigger.Hook -> processStateHook(hook = trigger.hook)
            }
        }
    }

    override val isActive: Boolean get() = phase != Phase.Cancelled
    override val state: StateFlow<State> = _state
        .onStart { start() }
        .stateIn(scope = machineScope, started = SharingStarted.Lazily, initialValue = _state.value)
    override val actions: SharedFlow<Action> = _actions
        .onStart { start() }
        .shareIn(scope = machineScope, started = SharingStarted.Lazily)
    override val effects: SharedFlow<Effect> = _effects
        .onStart { start() }
        .shareIn(scope = machineScope, started = SharingStarted.Lazily)

    override fun start() {
        if (phase != Phase.Idle) return
        phase = Phase.Running
        triggers.trySend(element = Trigger.Hook(hook = StateHook.OnEnter))
    }

    override fun dispatch(action: Action) = whenActive {
        triggers.trySend(element = Trigger.Action(action = action))
    }

    override fun onLifecycleEvent(event: LifecycleEvent) = whenActive {
        triggers.trySend(element = Trigger.Lifecycle(event = event))
    }

    override fun triggerStateHook(hook: StateHook<State>) = whenActive {
        triggers.trySend(element = Trigger.Hook(hook = hook))
    }

    override fun cancel() {
        phase = Phase.Cancelled
        jobRegistry.cancelAll()
        processingJob.cancel()
        triggers.close()
    }

    override fun invokeOnCompletion(handler: (cause: Throwable?) -> Unit): DisposableHandle =
        machineScope.coroutineContext.job.invokeOnCompletion(handler)

    private fun whenActive(block: () -> Unit) {
        if (isActive) block()
    }

    // ── Processing ────────────────────────────────────────────────────────────

    private suspend fun processAction(action: Action) {
        _actions.tryEmit(action)
        plugins.forEach { it.onAction(currentState = currentState, action = action) }
        val handlerType = HandlerType.Action(action = action)
        resolveActionHandler(state = currentState, action = action)?.handle(
            handlerType = handlerType,
            scope = actionScope(state = currentState, action = action),
            state = currentState,
        ) ?: plugins.forEach { it.onRejected(currentState = currentState, handlerType = handlerType) }
    }

    private suspend fun processLifecycleEvent(event: LifecycleEvent) {
        resolveHandler(handlerMap = lifecycleHandlers, state = currentState)?.get(key = event)?.handle(
            handlerType = HandlerType.Lifecycle(event = event),
            scope = lifecycleScope(state = currentState),
            state = currentState,
        )
    }

    private suspend fun processStateHook(hook: StateHook<State>) {
        when (hook) {
            StateHook.OnEnter -> resolveHandler(handlerMap = enterHandlers, state = currentState)?.handle(
                handlerType = HandlerType.Hook.Enter,
                scope = stateChangeScope(state = currentState),
                state = currentState,
            )

            StateHook.OnExit -> resolveHandler(handlerMap = exitHandlers, state = currentState)?.handle(
                handlerType = HandlerType.Hook.Exit,
                scope = stateChangeScope(state = currentState),
                state = currentState,
            )

            is StateHook.OnUpdate<*> -> {
                // Safe: hook is StateHook<State> so the only OnUpdate subtype it can be is OnUpdate<State>.
                @Suppress("UNCHECKED_CAST")
                val update = hook as StateHook.OnUpdate<State>
                resolveHandler(handlerMap = updateHandlers, state = currentState)?.handle(
                    handlerType = HandlerType.Hook.Update,
                    scope = updateHandlerScope(fromState = update.previousState, toState = currentState),
                    state = currentState,
                )
            }
        }
    }

    private suspend fun processEffects(effects: List<Effect>) = effects.forEach { effect ->
        plugins.forEach { it.onEffect(effect = effect) }
        _effects.emit(value = effect)
    }

    /**
     * Apply [result] originating from [fromState].
     *
     * [Plugin.onTransition] is called for every [HandlerResult.Transition], regardless of
     * whether it was produced by a dispatched action or a state/lifecycle hook.
     *
     * [processStateUpdate] is only fired for action-driven same-type value changes;
     * hook-driven transitions within the same type do not chain into [processStateUpdate]
     * to prevent cascading hook invocations.
     */
    private suspend fun processResult(
        handlerType: HandlerType<Action>,
        fromState: State,
        result: HandlerResult<State, Effect>,
    ) {
        when (result) {
            is HandlerResult.Transition -> processTransition(fromState = fromState, handlerType = handlerType, transition = result)
            is HandlerResult.SideEffect -> processEffects(effects = result.effects)
            HandlerResult.Rejected -> processRejected(fromState = fromState, handlerType = handlerType)
            HandlerResult.Done -> Unit
        }
    }

    private suspend fun processTransition(
        fromState: State,
        handlerType: HandlerType<Action>,
        transition: HandlerResult.Transition<State, Effect>,
    ) {
        val toState = transition.state
        _state.value = toState
        plugins.forEach { it.onTransition(fromState = fromState, toState = toState) }
        processEffects(effects = transition.effects)
        when {
            toState::class != fromState::class -> processStateChange(fromState = fromState, toState = toState)
            handlerType is HandlerType.Action && toState != fromState -> processStateUpdate(fromState = fromState, toState = toState)
        }
    }

    private fun processRejected(fromState: State, handlerType: HandlerType<Action>) {
        plugins.forEach { it.onRejected(currentState = fromState, handlerType = handlerType) }
    }

    /**
     * Fires the exit hook for [fromState] and the enter hook for [toState].
     *
     * Must be called only when [toState]'s type differs from [fromState]'s type.
     */
    private suspend fun processStateChange(fromState: State, toState: State) {
        jobRegistry.cancelAutoCancellable()
        resolveHandler(handlerMap = exitHandlers, state = fromState)?.handle(
            handlerType = HandlerType.Hook.Exit,
            scope = stateChangeScope(state = fromState),
            state = fromState,
        )
        resolveHandler(handlerMap = enterHandlers, state = toState)?.handle(
            handlerType = HandlerType.Hook.Enter,
            scope = stateChangeScope(state = toState),
            state = toState,
        )
    }

    private suspend fun processStateUpdate(fromState: State, toState: State) {
        resolveHandler(handlerMap = updateHandlers, state = toState)?.handle(
            handlerType = HandlerType.Hook.Update,
            scope = updateHandlerScope(fromState = fromState, toState = toState),
            state = toState,
        )
    }

    // ── Handler helpers ────────────────────────────────────────────────────

    /**
     * Returns all registered state classes that are supertypes of [state]'s class,
     * in the order they were registered across all handler maps.
     *
     * The result is computed once per unique state class using [KClass.isInstance] and
     * then cached in [ancestorCache], so every subsequent call for the same state class
     * is an O(1) map lookup. [registeredStates] is built at construction time
     * from the keys of all handler maps, bounding the scan to registered classes only.
     *
     * Safe to call without synchronization — [ancestorCache] is only ever written from
     * the single sequential processing coroutine.
     */
    private fun ancestorsFor(state: State): List<KClass<out State>> = ancestorCache.getOrPut(state::class) {
        registeredStates.filter { it != state::class && it.isInstance(state) }
    }

    /**
     * Resolve the handler for the given [state] + [action] pair.
     *
     * Priority:
     * 1. Exact state match — `actionHandlers[state::class][action::class]`
     * 2. Registered ancestor classes in insertion order, via [ancestorsFor].
     *    Register more-specific parent blocks after leaf blocks to control priority.
     * 3. `null` → [Plugin.onRejected] is called and the action is dropped.
     */
    private fun resolveActionHandler(state: State, action: Action): ActionHandler<State, Action, Effect>? {
        actionHandlers[state::class]?.get(key = action::class)?.let { return it }
        for (ancestorClass in ancestorsFor(state)) {
            actionHandlers[ancestorClass]?.get(action::class)?.let { return it }
        }
        return null
    }

    /**
     * Generic exact-match + ancestor lookup used by all handler maps except [actionHandlers].
     * Returns the first value whose registered [KClass] key matches [state] exactly,
     * or is a registered ancestor of it via [ancestorsFor].
     */
    private fun <Handler> resolveHandler(handlerMap: Map<KClass<out State>, Handler>, state: State): Handler? {
        handlerMap[state::class]?.let { return it }
        for (ancestorClass in ancestorsFor(state)) {
            handlerMap[ancestorClass]?.let { return it }
        }
        return null
    }

    /**
     * Run handler on [scope], then forward the returned [HandlerResult] into [processResult].
     *
     * On failure:
     * 1. If an `onError` hook is registered for [state], it is invoked with an [ErrorScope]
     *    carrying the raw [Throwable].
     *    - On success the hook's result is forwarded into [processResult].
     *    - If the hook itself throws, plugins are notified and no further recovery is attempted.
     * 2. If no hook is registered, plugins are notified directly.
     */
    private suspend fun <Scope : HandlerScope<State, Action, Effect, State>> Handler<Scope>.handle(
        handlerType: HandlerType<Action>,
        scope: Scope,
        state: State,
    ) = runCatching {
        scope.this()
        scope.consumeResult()
    }.onSuccess { result ->
        processResult(fromState = state, result = result, handlerType = handlerType)
    }.onFailure { throwable ->
        val stateErrorHandler = resolveHandler(handlerMap = errorHandlers, state = state)
        if (stateErrorHandler != null) {
            val recovery = errorScope(state = state, error = throwable, handlerType = handlerType)
            runCatching {
                recovery.stateErrorHandler()
                recovery.consumeResult()
            }.onSuccess { result ->
                processResult(fromState = state, result = result, handlerType = handlerType)
            }.onFailure {
                plugins.forEach { it.onError(error = throwable, currentState = state, handlerType = handlerType) }
            }
        } else {
            plugins.forEach { it.onError(error = throwable, currentState = state, handlerType = handlerType) }
        }
    }

    // ── Scope factories ───────────────────────────────────────────────────────

    private fun actionScope(state: State, action: Action) = ActionScope<State, Action, Effect, State, Action>(
        state = state,
        action = action,
        machineScope = machineScope,
        dispatch = ::dispatch,
        jobRegistry = jobRegistry,
    )

    private fun lifecycleScope(state: State) = LifecycleScope<State, Action, Effect, State>(
        state = state,
        machineScope = machineScope,
        dispatch = ::dispatch,
        jobRegistry = jobRegistry,
    )

    private fun stateChangeScope(state: State) = StateChangeScope<State, Action, Effect, State>(
        state = state,
        machineScope = machineScope,
        dispatch = ::dispatch,
        jobRegistry = jobRegistry,
    )

    private fun updateHandlerScope(fromState: State, toState: State) = StateUpdateScope<State, Action, Effect, State>(
        fromState = fromState,
        state = toState,
        machineScope = machineScope,
        dispatch = ::dispatch,
        jobRegistry = jobRegistry,
    )

    private fun errorScope(state: State, error: Throwable, handlerType: HandlerType<Action>) = ErrorScope<State, Action, Effect, State>(
        state = state,
        error = error,
        handlerType = handlerType,
        machineScope = machineScope,
        dispatch = ::dispatch,
        jobRegistry = jobRegistry,
    )

}
