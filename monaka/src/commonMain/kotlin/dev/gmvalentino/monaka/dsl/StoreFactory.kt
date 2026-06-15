package dev.gmvalentino.monaka.dsl

import kotlinx.coroutines.CoroutineScope
import dev.gmvalentino.monaka.core.DEFAULT_BUFFER_CAPACITY
import dev.gmvalentino.monaka.runtime.defaultCoroutineScope
import dev.gmvalentino.monaka.core.Action as ActionMarker
import dev.gmvalentino.monaka.core.Effect as EffectMarker
import dev.gmvalentino.monaka.core.State as StateMarker
import dev.gmvalentino.monaka.core.Store
import dev.gmvalentino.monaka.plugin.Plugin
import dev.gmvalentino.monaka.runtime.DefaultStore

/**
 * Create and start a [Store] using the declarative DSL.
 *
 * The machine begins processing actions immediately when [scope] is active.
 * Canceling [scope] (or calling [Store.stop]) stops the machine.
 *
 * ### Minimal example
 * ```kotlin
 * val store = store<CounterState, CounterAction, CounterEffect>(viewModelScope) {
 *     initialState(CounterState(count = 0))
 *
 *     state<CounterState> {
 *         on<CounterAction.Increment> { transition(state.copy(count = state.count + 1) })
 *         on<CounterAction.Decrement> { transition(state.copy(count = state.count - 1) })
 *     }
 *
 *     install(LoggingPlugin())
 * }
 * ```
 *
 * ### Runtime overrides
 * Pass [initialState] and/or [plugins] to override the values configured in [builder]:
 *
 * ```kotlin
 * val store = store<MyState, MyAction, MyEffect>(
 *     scope = scope,
 *     initialState = savedState,       // replaces initialState(…) in the builder block
 *     plugins = listOf(loggingPlugin), // appended after plugins installed in the builder block
 * ) {
 *     initialState(MyState.Idle)       // used only when savedState is null
 *     state<MyState> { … }
 * }
 * ```
 *
 * @param scope                 The [CoroutineScope] that drives action processing. On Android,
 *                              pass `viewModelScope` so the machine is automatically canceled
 *                              when the ViewModel is cleared.
 * @param name                  When non-null, overrides the name set via [StateMachineBuilder.name].
 *                              Exposed as [dev.gmvalentino.monaka.core.Store.name] on the running store.
 * @param initialState          When non-null, replaces the state set by [StateMachineBuilder.initialState].
 * @param plugins               Appended **after** any plugins installed inside [builder].
 * @param extraBufferCapacity `extraBufferCapacity` for the effects (and actions) [kotlinx.coroutines.flow.SharedFlow].
 *                              Increase this if your machine emits effects in rapid bursts so that
 *                              [kotlinx.coroutines.flow.MutableSharedFlow.emit] does not suspend and
 *                              stall the processing loop. Defaults to
 *                              [dev.gmvalentino.monaka.core.DEFAULT_BUFFER_CAPACITY].
 * @param initializer           Optional suspend function called once before the first action is
 *                              processed. Use this to restore persisted state from an async source
 *                              (e.g. `DataStore`, a database) at startup:
 *                              ```kotlin
 *                              val store = store(
 *                                  scope = viewModelScope,
 *                                  initializer = { dataStore.data.first().toLoginState() },
 *                              ) {
 *                                  initialState(LoginState.Idle) // fallback if initializer is null
 *                                  state<LoginState> { … }
 *                              }
 *                              ```
 *                              If the initializer throws, [dev.gmvalentino.monaka.plugin.Plugin.onError]
 *                              is called with [dev.gmvalentino.monaka.handler.HandlerType.Restore] and
 *                              the store falls back to [initialState]. `onEnter` always fires.
 * @param builder               DSL configuration block. Must call [StateMachineBuilder.initialState]
 *                              unless [initialState] is provided.
 */
public fun <State : StateMarker, Action : ActionMarker, Effect : EffectMarker> store(
    name: String? = null,
    scope: CoroutineScope = defaultCoroutineScope(),
    initialState: State? = null,
    plugins: List<Plugin> = emptyList(),
    extraBufferCapacity: Int = DEFAULT_BUFFER_CAPACITY,
    initializer: (suspend () -> State)? = null,
    builder: StateMachineBuilder<State, Action, Effect>.() -> Unit,
): Store<State, Action, Effect> {
    val stateMachine = StateMachineBuilder<State, Action, Effect>().apply(builder).build(initialState = initialState)
    return DefaultStore(
        id = stateMachine.id,
        name = name ?: stateMachine.name ?: stateMachine.id,
        initialState = stateMachine.initialState ?: error("initialState must be set inside the stateMachine builder or passed as the initialState argument to store()."),
        actionHandlers = stateMachine.actionHandlers,
        enterHandlers = stateMachine.enterHandlers,
        exitHandlers = stateMachine.exitHandlers,
        updateHandlers = stateMachine.updateHandlers,
        lifecycleHandlers = stateMachine.lifecycleHandlers,
        errorHandlers = stateMachine.errorHandlers,
        plugins = plugins,
        machineScope = scope,
        extraBufferCapacity = extraBufferCapacity,
        initializer = initializer,
    )
}

/**
 * Create and start a [Store] from an existing [StateMachine] configuration.
 *
 * Use this overload when the machine is defined as a [StateMachine] instance (e.g.
 * via [stateMachine]) and you want to start it with a separate scope.
 *
 * @param stateMachine          The configuration snapshot to run.
 * @param scope                 The [CoroutineScope] that drives action processing.
 * @param initialState          When non-null, overrides [StateMachine.initialState].
 * @param plugins               Appended **after** any plugins in [stateMachine].
 * @param extraBufferCapacity `extraBufferCapacity` for the effects (and actions) [kotlinx.coroutines.flow.SharedFlow].
 *                              Defaults to [dev.gmvalentino.monaka.core.DEFAULT_BUFFER_CAPACITY].
 * @param initializer           Optional suspend function called once before the first action is
 *                              processed. See the [store] DSL overload for full documentation.
 */
public fun <State : StateMarker, Action : ActionMarker, Effect : EffectMarker> store(
    stateMachine: StateMachine<State, Action, Effect>,
    name: String? = null,
    scope: CoroutineScope = defaultCoroutineScope(),
    initialState: State? = null,
    plugins: List<Plugin> = emptyList(),
    extraBufferCapacity: Int = DEFAULT_BUFFER_CAPACITY,
    initializer: (suspend () -> State)? = null,
): Store<State, Action, Effect> = DefaultStore(
    id = stateMachine.id,
    name = name ?: stateMachine.name ?: stateMachine::class.simpleName ?: stateMachine.id,
    initialState = initialState ?: stateMachine.initialState ?: error("initialState must be set inside the stateMachine builder or passed as the initialState argument to store()."),
    actionHandlers = stateMachine.actionHandlers,
    enterHandlers = stateMachine.enterHandlers,
    exitHandlers = stateMachine.exitHandlers,
    updateHandlers = stateMachine.updateHandlers,
    lifecycleHandlers = stateMachine.lifecycleHandlers,
    errorHandlers = stateMachine.errorHandlers,
    plugins = plugins,
    machineScope = scope,
    extraBufferCapacity = extraBufferCapacity,
    initializer = initializer,
)
