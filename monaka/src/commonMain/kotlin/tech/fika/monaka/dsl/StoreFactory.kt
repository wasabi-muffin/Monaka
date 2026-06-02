package tech.fika.monaka.dsl

import kotlinx.coroutines.CoroutineScope
import tech.fika.monaka.core.Action as ActionMarker
import tech.fika.monaka.core.Effect as EffectMarker
import tech.fika.monaka.core.State as StateMarker
import tech.fika.monaka.core.Store
import tech.fika.monaka.plugin.Plugin
import tech.fika.monaka.runtime.DefaultStore

/**
 * Create and start a [Store] using the declarative DSL.
 *
 * The machine begins processing actions immediately when [scope] is active.
 * Cancelling [scope] (or calling [Store.cancel]) stops the machine.
 *
 * ### Minimal example
 * ```kotlin
 * val machine = store<CounterState, CounterAction, CounterEffect>(viewModelScope) {
 *     initialState(CounterState(count = 0))
 *
 *     state<CounterState> {
 *         on<CounterAction.Increment> { transition { state.copy(count = state.count + 1) } }
 *         on<CounterAction.Decrement> { transition { state.copy(count = state.count - 1) } }
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
 * val machine = store<MyState, MyAction, MyEffect>(
 *     scope = scope,
 *     initialState = savedState,       // replaces initialState(…) in the builder block
 *     plugins = listOf(loggingPlugin), // appended after plugins installed in the builder block
 * ) {
 *     initialState(MyState.Idle)       // used only when savedState is null
 *     state<MyState> { … }
 * }
 * ```
 *
 * @param scope        The [CoroutineScope] that drives action processing. On Android,
 *                     pass `viewModelScope` so the machine is automatically cancelled
 *                     when the ViewModel is cleared.
 * @param initialState When non-null, replaces the state set by [StateMachineBuilder.initialState].
 * @param plugins      Appended **after** any plugins installed inside [builder].
 * @param builder      DSL configuration block. Must call [StateMachineBuilder.initialState]
 *                     unless [initialState] is provided.
 */
fun <State : StateMarker, Action : ActionMarker, Effect : EffectMarker> store(
    scope: CoroutineScope,
    initialState: State? = null,
    plugins: List<Plugin<State, Action, Effect>> = emptyList(),
    builder: StateMachineBuilder<State, Action, Effect>.() -> Unit,
): Store<State, Action, Effect> {
    val config = StateMachineBuilder<State, Action, Effect>().apply(builder).build(initialState = initialState, extraPlugins = plugins)
    return DefaultStore(
        id = config.id,
        initialState = config.initialState,
        actionHandlers = config.actionHandlers,
        enterHandlers = config.enterHandlers,
        exitHandlers = config.exitHandlers,
        updateHandlers = config.updateHandlers,
        lifecycleHandlers = config.lifecycleHandlers,
        errorHandlers = config.errorHandlers,
        errorMapper = config.errorMapper,
        plugins = config.plugins,
        machineScope = scope,
    )
}

/**
 * Create and start a [Store] from an existing [StateMachine] configuration.
 *
 * Use this overload when the machine is defined as a [StateMachine] instance (e.g.
 * via [stateMachine]) and you want to start it with a separate scope.
 *
 * @param stateMachine The configuration snapshot to run.
 * @param scope        The [CoroutineScope] that drives action processing.
 * @param initialState When non-null, overrides [StateMachine.initialState].
 * @param plugins      Appended **after** any plugins in [stateMachine].
 */
fun <State : StateMarker, Action : ActionMarker, Effect : EffectMarker> store(
    stateMachine: StateMachine<State, Action, Effect>,
    scope: CoroutineScope,
    initialState: State? = null,
    plugins: List<Plugin<State, Action, Effect>> = emptyList(),
): Store<State, Action, Effect> = DefaultStore(
    id = stateMachine.id,
    initialState = initialState ?: stateMachine.initialState,
    actionHandlers = stateMachine.actionHandlers,
    enterHandlers = stateMachine.enterHandlers,
    exitHandlers = stateMachine.exitHandlers,
    updateHandlers = stateMachine.updateHandlers,
    lifecycleHandlers = stateMachine.lifecycleHandlers,
    errorHandlers = stateMachine.errorHandlers,
    errorMapper = stateMachine.errorMapper,
    plugins = stateMachine.plugins + plugins,
    machineScope = scope,
)