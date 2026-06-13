package dev.gmvalentino.monaka.examples.login

import dev.gmvalentino.monaka.dsl.StateMachine
import dev.gmvalentino.monaka.dsl.stateMachine
import dev.gmvalentino.monaka.plugin.LoggingPlugin

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
            transition(state.toTyping(username = action.username, password = action.password))
        }
    }

    state<LoginState.Typing> {
        on<LoginAction.UpdateCredentials> {
            transition(state.copy(username = action.username, password = action.password))
        }

        on<LoginAction.Submit> {
            if (!state.isValid) {
                sideEffect(LoginEffect.ShowValidationError("Please fill in all fields."))
            } else {
                transition(state.toSubmitting())
            }
        }
    }

    state<LoginState.Submitting> {
        onEnter {
            val username = loginRepository.login(state.username, state.password)
            transition(state.toAuthenticated(username = username))
            sideEffect(LoginEffect.NavigateToHome)
        }

        onError {
            transition(state.toError(message = error.message ?: ""))
        }
    }

    state<LoginState.Error> {
        on<LoginAction.UpdateCredentials> {
            transition(state.copy(username = action.username, password = action.password))
        }

        on<LoginAction.Retry> {
            transition(state.toSubmitting())
        }
    }

    // Logout is valid from any state — registered on the parent sealed interface
    state<LoginState> {
        on<LoginAction.Logout> {
            transition(LoginState.Idle)
            sideEffect(LoginEffect.NavigateToLogin)
        }
    }
})
