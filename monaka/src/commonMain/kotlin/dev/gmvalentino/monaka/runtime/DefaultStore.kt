package dev.gmvalentino.monaka.runtime

import dev.gmvalentino.monaka.core.DEFAULT_BUFFER_CAPACITY
import dev.gmvalentino.monaka.core.InternalMonakaApi
import dev.gmvalentino.monaka.core.LifecycleEvent
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
import kotlin.reflect.KClass
import dev.gmvalentino.monaka.core.Action as ActionMarker
import dev.gmvalentino.monaka.core.Effect as EffectMarker
import dev.gmvalentino.monaka.core.State as StateMarker

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
 *
 * ### Store lifetime and cleanup
 * Callbacks registered via [invokeOnCompletion] are attached to [machineScope]'s job. They fire
 * when [machineScope] is canceled (e.g. `viewModelScope` cleared on Android). Calling [stop]
 * directly cancels the internal processing coroutine and closes the trigger channel, but does
 * **not** cancel [machineScope], so [invokeOnCompletion] handlers do **not** fire on an explicit
 * [stop] call. To receive cleanup notifications in both cases, cancel the owning scope rather
 * than calling [stop] directly, or unregister manually via [StoreRegistry.unregister].
 */
@OptIn(InternalMonakaApi::class)
internal class DefaultStore<State : StateMarker, Action : ActionMarker, Effect : EffectMarker>(
    override val id: String,
    override val name: String,
    initialState: State,
    private val actionHandlers: Map<KClass<out State>, Map<KClass<out Action>, ActionHandler<State, Action, Effect>>>,
    private val enterHandlers: Map<KClass<out State>, StateChangeHandler<State, Action, Effect>>,
    private val exitHandlers: Map<KClass<out State>, StateChangeHandler<State, Action, Effect>>,
    private val updateHandlers: Map<KClass<out State>, StateUpdateHandler<State, Action, Effect>>,
    private val lifecycleHandlers: Map<KClass<out State>, Map<LifecycleEvent, LifecycleHandler<State, Action, Effect>>>,
    private val errorHandlers: Map<KClass<out State>, StateErrorHandler<State, Action, Effect>>,
    plugins: List<Plugin>,
    private val machineScope: CoroutineScope = defaultCoroutineScope(),
    private val extraBufferCapacity: Int = DEFAULT_BUFFER_CAPACITY,
    private val initializer: (suspend () -> State)? = null,
) : Store<State, Action, Effect> {
    private val plugins: MutableList<Plugin> = plugins.toMutableList()

    private enum class Phase { Idle, Running, Canceled }

    private var phase = Phase.Idle

    private val _state = MutableStateFlow(initialState)
    private val _actions = MutableSharedFlow<Action>(extraBufferCapacity = extraBufferCapacity)
    private val _effects = MutableSharedFlow<Effect>(extraBufferCapacity = extraBufferCapacity)

    private val triggers = Channel<Trigger<State, Action>>(Channel.UNLIMITED)
    private val currentState: State get() = _state.value

    private val resolver = HandlerResolver(
        actionHandlers = actionHandlers,
        enterHandlers = enterHandlers,
        exitHandlers = exitHandlers,
        updateHandlers = updateHandlers,
        lifecycleHandlers = lifecycleHandlers,
        errorHandlers = errorHandlers,
    )

    private val jobRegistry = JobRegistry()
    private val processingJob = machineScope.launch {
        for (trigger in triggers) {
            when (trigger) {
                is Trigger.Action -> processAction(action = trigger.action)
                is Trigger.Lifecycle -> processLifecycleEvent(event = trigger.event)
                is Trigger.Hook -> processStateHook(hook = trigger.hook)
                is Trigger.Restore -> processRestore(initializer = trigger.initializer)
            }
        }
    }.also { job ->
        job.invokeOnCompletion { stop() }
    }

    override val isActive: Boolean get() = phase != Phase.Canceled
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
        if (initializer != null) {
            triggers.trySend(element = Trigger.Restore(initializer = initializer))
        } else {
            triggers.trySend(element = Trigger.Hook(hook = StateHook.OnEnter))
        }
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

    override fun stop() {
        phase = Phase.Canceled
        jobRegistry.cancelAll()
        processingJob.cancel()
        triggers.close()
    }

    override fun invokeOnCompletion(handler: (cause: Throwable?) -> Unit): DisposableHandle = machineScope.coroutineContext.job.invokeOnCompletion(handler)

    override fun install(plugin: Plugin) {
        plugins.add(plugin)
    }

    private fun whenActive(block: () -> Unit) {
        if (isActive) block()
    }

    // ── Plugin invocation ─────────────────────────────────────────────────────

    /**
     * Invokes [block] on every installed plugin, catching and discarding any exception thrown by
     * an individual plugin so that a misbehaving plugin cannot cancel the processing coroutine or
     * prevent other plugins from running.
     */
    private inline fun invokePlugins(block: Plugin.() -> Unit) {
        plugins.forEach { plugin ->
            runCatching { plugin.block() }
        }
    }

    // ── Processing ────────────────────────────────────────────────────────────

    private suspend fun processAction(action: Action) {
        _actions.tryEmit(action)
        invokePlugins { onAction(currentState = currentState, action = action) }
        val handlerType = HandlerType.Action(action = action)
        resolver.resolveActionHandler(state = currentState, action = action)?.handle(
            handlerType = handlerType,
            scope = actionScope(state = currentState, action = action),
            state = currentState,
        ) ?: invokePlugins { onUnhandled(currentState = currentState, action = action) }
    }

    private suspend fun processLifecycleEvent(event: LifecycleEvent) {
        resolver.resolveHandler(handlerMap = lifecycleHandlers, state = currentState)?.get(key = event)?.handle(
            handlerType = HandlerType.Lifecycle(event = event),
            scope = lifecycleScope(state = currentState),
            state = currentState,
        )
    }

    private suspend fun processStateHook(hook: StateHook<State>) {
        when (hook) {
            StateHook.OnEnter -> resolver.resolveHandler(handlerMap = enterHandlers, state = currentState)?.handle(
                handlerType = HandlerType.Hook.Enter,
                scope = stateChangeScope(state = currentState),
                state = currentState,
            )

            StateHook.OnExit -> resolver.resolveHandler(handlerMap = exitHandlers, state = currentState)?.handle(
                handlerType = HandlerType.Hook.Exit,
                scope = stateChangeScope(state = currentState),
                state = currentState,
            )

            is StateHook.OnUpdate<*> -> {
                // Safe: hook is StateHook<State> so the only OnUpdate subtype it can be is OnUpdate<State>.
                @Suppress("UNCHECKED_CAST")
                val update = hook as StateHook.OnUpdate<State>
                resolver.resolveHandler(handlerMap = updateHandlers, state = currentState)?.handle(
                    handlerType = HandlerType.Hook.Update,
                    scope = updateHandlerScope(fromState = update.previousState, toState = currentState),
                    state = currentState,
                )
            }
        }
    }

    /**
     * Calls [initializer] to load persisted state, then fires `onEnter` for the resulting state.
     *
     * If [initializer] throws, [Plugin.onError] is called with [HandlerType.Restore] and the
     * store continues with its configured `initialState`. `onEnter` always fires regardless of
     * whether the initializer succeeded, so the machine always reaches a usable initial state.
     */
    private suspend fun processRestore(initializer: suspend () -> State) {
        runCatching {
            _state.value = initializer()
        }.onFailure { error ->
            invokePlugins { onError(error = error, currentState = currentState, handlerType = HandlerType.Restore) }
        }
        processStateHook(hook = StateHook.OnEnter)
    }

    private suspend fun processEffects(effects: List<Effect>) = effects.forEach { effect ->
        invokePlugins { onEffect(effect = effect) }
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
        invokePlugins { onTransition(fromState = fromState, toState = toState) }
        processEffects(effects = transition.effects)
        when {
            toState::class != fromState::class -> processStateChange(fromState = fromState, toState = toState)
            handlerType is HandlerType.Action && toState != fromState -> processStateUpdate(fromState = fromState, toState = toState)
        }
    }

    private fun processRejected(fromState: State, handlerType: HandlerType<Action>) {
        invokePlugins { onRejected(currentState = fromState, handlerType = handlerType) }
    }

    /**
     * Fires the exit hook for [fromState] and the enter hook for [toState].
     *
     * Must be called only when [toState]'s type differs from [fromState]'s type.
     */
    private suspend fun processStateChange(fromState: State, toState: State) {
        jobRegistry.cancelAutoCancellable()
        resolver.resolveHandler(handlerMap = exitHandlers, state = fromState)?.handle(
            handlerType = HandlerType.Hook.Exit,
            scope = stateChangeScope(state = fromState),
            state = fromState,
        )
        resolver.resolveHandler(handlerMap = enterHandlers, state = toState)?.handle(
            handlerType = HandlerType.Hook.Enter,
            scope = stateChangeScope(state = toState),
            state = toState,
        )
    }

    private suspend fun processStateUpdate(fromState: State, toState: State) {
        resolver.resolveHandler(handlerMap = updateHandlers, state = toState)?.handle(
            handlerType = HandlerType.Hook.Update,
            scope = updateHandlerScope(fromState = fromState, toState = toState),
            state = toState,
        )
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
        val stateErrorHandler = resolver.resolveHandler(handlerMap = errorHandlers, state = state)
        if (stateErrorHandler != null) {
            val recovery = errorScope(state = state, error = throwable, handlerType = handlerType)
            runCatching {
                recovery.stateErrorHandler()
                recovery.consumeResult()
            }.onSuccess { result ->
                processResult(fromState = state, result = result, handlerType = handlerType)
            }.onFailure {
                invokePlugins { onError(error = throwable, currentState = state, handlerType = handlerType) }
            }
        } else {
            invokePlugins { onError(error = throwable, currentState = state, handlerType = handlerType) }
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
