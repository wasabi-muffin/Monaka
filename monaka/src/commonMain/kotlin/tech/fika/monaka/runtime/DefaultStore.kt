package tech.fika.monaka.runtime

import kotlin.reflect.KClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tech.fika.monaka.core.Action as ActionMarker
import tech.fika.monaka.core.Effect as EffectMarker
import tech.fika.monaka.error.ErrorMapper
import tech.fika.monaka.handler.HandlerResult
import tech.fika.monaka.handler.HandlerType
import tech.fika.monaka.core.LifecycleEvent
import tech.fika.monaka.core.State as StateMarker
import tech.fika.monaka.core.Store
import tech.fika.monaka.handler.ActionHandler
import tech.fika.monaka.dsl.ActionScope
import tech.fika.monaka.dsl.ErrorScope
import tech.fika.monaka.handler.Handler
import tech.fika.monaka.dsl.HandlerScope
import tech.fika.monaka.dsl.consumeResult
import tech.fika.monaka.handler.LifecycleHandler
import tech.fika.monaka.dsl.LifecycleScope
import tech.fika.monaka.handler.StateChangeHandler
import tech.fika.monaka.dsl.StateChangeScope
import tech.fika.monaka.handler.StateErrorHandler
import tech.fika.monaka.handler.StateUpdateHandler
import tech.fika.monaka.dsl.StateUpdateScope
import tech.fika.monaka.plugin.Plugin

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
 * All keyed jobs (`launch(key) { }`) are tracked in [jobRegistry]. Jobs must be
 * explicitly cancelled via `cancel(key)` in `onExit` hooks or action handlers.
 * Use `onExit { cancel("key") }` to clean up jobs when leaving a state.
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
internal class DefaultStore<State : StateMarker, Action : ActionMarker, Effect : EffectMarker>(
    override val id: String,
    initialState: State,
    private val actionHandlers: Map<KClass<*>, Map<KClass<*>, ActionHandler<State, Action, Effect>>>,
    private val enterHandlers: Map<KClass<*>, StateChangeHandler<State, Action, Effect>>,
    private val exitHandlers: Map<KClass<*>, StateChangeHandler<State, Action, Effect>>,
    private val updateHandlers: Map<KClass<*>, StateUpdateHandler<State, Action, Effect>>,
    private val lifecycleHandlers: Map<KClass<*>, Map<LifecycleEvent, LifecycleHandler<State, Action, Effect>>>,
    private val errorHandlers: Map<KClass<*>, StateErrorHandler<State, Action, Effect>>,
    private val errorMapper: ErrorMapper,
    private val plugins: List<Plugin<State, Action, Effect>>,
    private val machineScope: CoroutineScope,
) : Store<State, Action, Effect> {

    private val _state = MutableStateFlow(initialState)
    private val _actions = MutableSharedFlow<Action>(extraBufferCapacity = 64)
    private val _effects = MutableSharedFlow<Effect>(extraBufferCapacity = 64)
    private val triggers = Channel<Trigger<Action>>(Channel.UNLIMITED)
    private val jobRegistry = JobRegistry()
    private val currentState: State get() = _state.value
    private val processingJob = machineScope.launch {
        for (trigger in triggers) {
            when (trigger) {
                is Trigger.Action -> processAction(action = trigger.action)
                is Trigger.Lifecycle -> processLifecycleEvent(trigger.event)
            }
        }
    }

    override val state: StateFlow<State> = _state.asStateFlow()
    override val actions: SharedFlow<Action> = _actions.asSharedFlow()
    override val effects: SharedFlow<Effect> = _effects.asSharedFlow()

    override fun dispatch(action: Action) {
        plugins.forEach { it.onAction(currentState = currentState, action = action) }
        _actions.tryEmit(value = action)
        triggers.trySend(element = Trigger.Action(action = action))
    }

    override fun onLifecycleEvent(event: LifecycleEvent) {
        triggers.trySend(element = Trigger.Lifecycle(event = event))
    }

    override fun cancel() {
        jobRegistry.cancelAll()
        processingJob.cancel()
        triggers.close()
    }

    // ── Processing ────────────────────────────────────────────────────────────

    private suspend fun processAction(action: Action) {
        resolveActionHandler(state = currentState, action = action)?.handle(
            handlerType = HandlerType.Action(action = action),
            scope = actionScope(state = currentState, action = action),
            state = currentState,
        ) ?: plugins.forEach { it.onInvalid(currentState = currentState, action = action) }
    }

    private suspend fun processLifecycleEvent(event: LifecycleEvent) {
        resolveHandler(handlerMap = lifecycleHandlers, state = currentState)?.get(key = event)?.handle(
            handlerType = HandlerType.Lifecycle(event = event),
            scope = lifecycleScope(state = currentState),
            state = currentState,
        )
    }

    private suspend fun processEffects(effects: List<Effect>) = effects.forEach { effect ->
        plugins.forEach { it.onEffect(effect = effect) }
        _effects.emit(value = effect)
    }

    /**
     * Apply [result] originating from [fromState].
     *
     * When [handlerType] is [HandlerType.Action] (i.e. the result was produced by a dispatched action):
     * - Notifies plugins via [Plugin.onTransition].
     * - Fires [processStateUpdate] when the state value changes within the same type.
     *
     * When [handlerType] is anything else (i.e. the result was returned by a state or lifecycle hook):
     * - Skips plugin notification and the update hook; only fires [processStateChange] if the
     *   state type changes.
     */
    private suspend fun processResult(
        handlerType: HandlerType<Action>,
        fromState: State,
        result: HandlerResult<State, Effect>,
    ) {
        val action = (handlerType as? HandlerType.Action)?.action
        when (result) {
            is HandlerResult.Transition -> processTransition(fromState = fromState, action = action, transition = result)
            is HandlerResult.SideEffect -> processEffects(effects = result.effects)
            HandlerResult.Rejected -> processRejected(fromState = fromState, action = action)
            HandlerResult.Done -> Unit
        }
    }

    private suspend fun processTransition(
        fromState: State,
        action: Action?,
        transition: HandlerResult.Transition<State, Effect>,
    ) {
        val toState = transition.state
        _state.value = toState
        if (action != null) {
           plugins.forEach { it.onTransition(fromState = fromState, toState = toState, action = action) }
        }
        processEffects(effects = transition.effects)
        when {
            toState::class != fromState::class -> processStateChange(fromState = fromState, toState = toState)
            action != null && toState != fromState -> processStateUpdate(fromState = fromState, toState = toState)
        }
    }

    private fun processRejected(fromState: State, action: Action? = null) {
        if (action != null) {
            plugins.forEach { it.onInvalid(currentState = fromState, action = action) }
        }
    }

    /**
     * Fires the exit hook for [fromState] and the enter hook for [toState].
     *
     * Must be called only when [toState]'s type differs from [fromState]'s type.
     */
    private suspend fun processStateChange(fromState: State, toState: State) {
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
     * Resolve the handler for the given [state] + [action] pair.
     *
     * Priority:
     * 1. Exact state match — `stateHandlers[state::class][action::class]`
     * 2. Supertype scan — iterates registered state classes in insertion order
     *    and returns the first whose `isInstance(state)` check passes.
     *    Register more-specific parent blocks after leaf blocks to control priority.
     * 3. `null` → [Plugin.onInvalid] is called and the action is dropped.
     */
    private fun resolveActionHandler(state: State, action: Action): ActionHandler<State, Action, Effect>? {
        actionHandlers[state::class]?.get(key = action::class)?.let { return it }
        for ((stateClass, actionHandlers) in actionHandlers) {
            if (stateClass != state::class && stateClass.isInstance(value = state)) {
                actionHandlers[action::class]?.let { return it }
            }
        }
        return null
    }

    /**
     * Generic exact-match + supertype scan used by all handler maps except [actionHandlers].
     * Returns the first value whose registered [KClass] key matches [state] exactly,
     * or is a supertype of it via [KClass.isInstance].
     */
    private fun <Handler> resolveHandler(handlerMap: Map<KClass<*>, Handler>, state: State): Handler? {
        handlerMap[state::class]?.let { return it }
        for ((stateClass, handler) in handlerMap) {
            if (stateClass != state::class && stateClass.isInstance(value = state)) return handler
        }
        return null
    }

    /**
     * Run [handler] on [scope], then forward the returned [HandlerResult] into [processResult].
     *
     * On failure:
     * 1. The raw throwable is mapped to a [tech.fika.monaka.error.AppError] via [errorMapper].
     * 2. If an `onError` hook is registered for [state], it is invoked with an [ErrorScope].
     *    - On success the hook's result is forwarded into [processResult].
     *    - If the hook itself throws, plugins are notified and no further recovery is attempted.
     * 3. If no hook is registered, plugins are notified directly.
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
        val error = errorMapper(throwable)
        val stateErrorHandler = resolveHandler(handlerMap = errorHandlers, state = state)
        if (stateErrorHandler != null) {
            val recovery = errorScope(state = state, error = error, handlerType = handlerType)
            runCatching {
                recovery.stateErrorHandler()
                recovery.consumeResult()
            }
                .onSuccess { result ->
                    processResult(fromState = state, result = result, handlerType = handlerType)
                }
                .onFailure {
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

    private fun errorScope(state: State, error: tech.fika.monaka.error.AppError, handlerType: HandlerType<Action>) =
        ErrorScope<State, Action, Effect, State>(
            state = state,
            error = error,
            handlerType = handlerType,
            machineScope = machineScope,
            dispatch = ::dispatch,
            jobRegistry = jobRegistry,
        )
}
