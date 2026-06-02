package tech.fika.monaka.examples.login

import tech.fika.monaka.dsl.StateMachine
import tech.fika.monaka.dsl.stateMachine
import tech.fika.monaka.plugin.LoggingPlugin

/**
 * Login machine using the **onEnter** hook pattern.
 *
 * `Submit` immediately transitions to `Submitting`, then the `onEnter` hook
 * calls the repository as a suspend function. When the call completes, the
 * machine transitions to `Authenticated` or `Error` directly.
 *
 * The [loginRepository] is injected at the call site (ViewModel constructor)
 * and captured by the closure — no special DI mechanism needed.
 *
 * ```kotlin
 * class LoginViewModel(
 *     private val loginRepository: LoginRepository,
 * ) : ViewModel() {
 *     val store = LoginStateMachine(viewModelScope, loginRepository)
 * }
 * ```
 */
class LoginStateMachine(
    loginRepository: LoginRepository,
) : StateMachine<LoginState, LoginAction, LoginEffect> by stateMachine(builder = {
    initialState(LoginState.Idle)

    state<LoginState.Idle> {
        on<LoginAction.UpdateCredentials> {
            transition { LoginState.Typing(action.username, action.password) }
        }
    }

    state<LoginState.Typing> {
        on<LoginAction.UpdateCredentials> {
            transition { state.copy(username = action.username, password = action.password) }
        }

        on<LoginAction.Submit> {
            if (!state.isValid) {
                sideEffect(LoginEffect.ShowValidationError("Please fill in all fields."))
            } else {
                transition { LoginState.Submitting(state.username, state.password) }
            }
        }
    }

    state<LoginState.Submitting> {
        onEnter {
            val username = loginRepository.login(state.username, state.password)
            transition(LoginEffect.NavigateToHome) { LoginState.Authenticated(username) }
        }

        onError {
            transition {
                LoginState.Error(state.username, state.password, error.message ?: "")
            }
        }
    }

    state<LoginState.Error> {
        on<LoginAction.UpdateCredentials> {
            transition { state.copy(username = action.username, password = action.password) }
        }

        on<LoginAction.Retry> {
            transition { LoginState.Submitting(state.username, state.password) }
        }
    }

    // Logout is valid from any state — registered on the parent sealed interface
    state<LoginState> {
        on<LoginAction.Logout> {
            transition(LoginEffect.NavigateToLogin) { LoginState.Idle }
        }
    }

    install(LoggingPlugin(tag = "Login"))
})
