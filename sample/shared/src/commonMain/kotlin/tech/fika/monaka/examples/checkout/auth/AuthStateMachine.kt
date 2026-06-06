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
            transition { state.toSigningIn(username = action.username, password = action.password) }
        }
    }

    state<AuthState.SigningIn> {
        onEnter {
            runCatching { authRepository.signIn(state.username, state.password) }
                .fold(
                    onSuccess = { user -> transition { state.toSignedIn(user = user) } },
                    onFailure = { e -> transition { state.toSignInFailed(reason = e.message ?: "Sign-in failed") } },
                )
        }
    }

    state<AuthState.SignInFailed> {
        on<AuthAction.Attempt> {
            transition { state.toSigningIn(username = action.username, password = action.password) }
        }
    }

    state<AuthState.SignedIn> {
        on<AuthAction.SignOut> { transition { state.toSignedOut() } }
    }

    install(LoggingPlugin(tag = "Auth"))
})
