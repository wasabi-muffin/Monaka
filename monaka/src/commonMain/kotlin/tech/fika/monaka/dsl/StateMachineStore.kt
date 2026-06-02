@file:OptIn(ExperimentalUuidApi::class)

package tech.fika.monaka.dsl

import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.CoroutineScope
import tech.fika.monaka.core.Action as ActionMarker
import tech.fika.monaka.core.Effect as EffectMarker
import tech.fika.monaka.core.State as StateMarker
import tech.fika.monaka.core.Store
import tech.fika.monaka.plugin.Plugin
import tech.fika.monaka.runtime.DefaultStore

/**
 * Base class for defining a [Store] as a named class rather than a factory lambda.
 *
 * Extend this class and configure the machine inside an `init` block using the same
 * DSL methods available in [stateMachine]. The internal machine is built lazily the
 * first time [state], [effects], [dispatch], or [cancel] is accessed, so all `init`
 * blocks in the subclass are guaranteed to run first.
 *
 * ### Example
 * ```kotlin
 * class LoginStateMachine(
 *     scope: CoroutineScope,
 *     loginRepository: LoginRepository,
 * ) : StateMachine<LoginState, LoginAction, LoginEffect>(scope) {
 *
 *     init {
 *         initialState(LoginState.Idle)
 *
 *         state<LoginState.Idle> {
 *             on<LoginAction.UpdateCredentials> {
 *                 transition { LoginState.Typing(action.username, action.password) }
 *             }
 *         }
 *
 *         state<LoginState.Typing> {
 *             on<LoginAction.Submit> {
 *                 transition { LoginState.Submitting }
 *             }
 *         }
 *
 *         state<LoginState.Submitting> {
 *             onEnter {
 *                 when (val r = loginRepository.login(state.username, state.password)) {
 *                     is Success -> transition(LoginEffect.NavigateToHome) { LoginState.Authenticated(r.username) }
 *                     is Failure -> transition { LoginState.Error(r.reason) }
 *                 }
 *             }
 *         }
 *
 *         state<LoginState> {
 *             on<LoginAction.Logout> {
 *                 transition { LoginState.Idle }
 *                 sideEffect(LoginEffect.NavigateToLogin)
 *             }
 *         }
 *
 *         install(LoggingPlugin(tag = "Login"))
 *     }
 * }
 * ```
 *
 * ### Runtime overrides
 * Pass [initialState] and/or [plugins] to the constructor to override the values
 * configured inside the `init` block. This is useful when injecting a starting state
 * from outside the class (e.g., a saved-state handle) or when adding platform-specific
 * plugins in the subclass constructor before the machine starts.
 *
 * ```kotlin
 * class LoginStateMachine(
 *     scope: CoroutineScope,
 *     savedState: LoginState? = null,
 *     extraPlugins: List<Plugin<LoginState, LoginAction, LoginEffect>> = emptyList(),
 * ) : StateMachine<LoginState, LoginAction, LoginEffect>(
 *     scope = scope,
 *     initialState = savedState,
 *     plugins = extraPlugins,
 * ) { … }
 * ```
 *
 * - [initialState]: when non-null, replaces the state set by [tech.fika.monaka.dsl.StateMachineBuilder.initialState].
 * - [plugins]: appended **after** any plugins installed inside the `init` block.
 */
class StateMachineStore<State : StateMarker, Action : ActionMarker, Effect : EffectMarker>(
    val stateMachine: StateMachine<State, Action, Effect>,
    private val scope: CoroutineScope,
    private val initialState: State? = null,
    private val plugins: List<Plugin<State, Action, Effect>> = emptyList(),
) : Store<State, Action, Effect> by DefaultStore(
    machineScope = scope,
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
)
