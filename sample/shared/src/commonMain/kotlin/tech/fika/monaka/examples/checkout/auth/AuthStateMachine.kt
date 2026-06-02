package tech.fika.monaka.examples.checkout.auth

import tech.fika.monaka.dsl.StateMachine
import tech.fika.monaka.dsl.stateMachine
import tech.fika.monaka.examples.checkout.data.AuthRepository
import tech.fika.monaka.plugin.LoggingPlugin

class AuthStateMachine(
    authRepository: AuthRepository,
) : StateMachine<AuthState, AuthAction, AuthEffect> by stateMachine(builder = {
    initialState(AuthState.SignedOut)

    state<AuthState.SignedOut> {
        on<AuthAction.Attempt> {
            transition {
                AuthState.SigningIn(action.username, action.password)
            }
        }
    }

    state<AuthState.SigningIn> {
        onEnter {
            runCatching { authRepository.signIn(state.username, state.password) }
                .fold(
                    onSuccess = { user -> transition { AuthState.SignedIn(user) } },
                    onFailure = { e -> transition { AuthState.SignInFailed(e.message ?: "Sign-in failed") } },
                )
        }
    }

    state<AuthState.SignInFailed> {
        on<AuthAction.Attempt> {
            transition {
                AuthState.SigningIn(action.username, action.password)
            }
        }
    }

    state<AuthState.SignedIn> {
        on<AuthAction.SignOut> { transition { AuthState.SignedOut } }
    }

    install(LoggingPlugin(tag = "Auth"))
})
