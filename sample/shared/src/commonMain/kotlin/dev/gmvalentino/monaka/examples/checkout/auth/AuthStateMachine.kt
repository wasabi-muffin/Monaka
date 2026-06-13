package dev.gmvalentino.monaka.examples.checkout.auth

import dev.gmvalentino.monaka.dsl.StateMachine
import dev.gmvalentino.monaka.dsl.stateMachine
import dev.gmvalentino.monaka.examples.checkout.data.AuthRepository
import dev.gmvalentino.monaka.plugin.LoggingPlugin

class AuthStateMachine(
    authRepository: AuthRepository,
) : StateMachine<AuthState, AuthAction, AuthEffect> by stateMachine(builder = {
    initialState(AuthState.SignedOut)

    state<AuthState.SignedOut> {
        on<AuthAction.Attempt> {
            transition(state.toSigningIn(username = action.username, password = action.password))
        }
    }

    state<AuthState.SigningIn> {
        onEnter {
            runCatching { authRepository.signIn(state.username, state.password) }
                .onSuccess { user -> transition(state.toSignedIn(user = user)) }
                .onFailure { error -> transition(state.toSignInFailed(reason = error.message ?: "Sign-in failed")) }
        }
    }

    state<AuthState.SignInFailed> {
        on<AuthAction.Attempt> {
            transition(state.toSigningIn(username = action.username, password = action.password))
        }
    }

    state<AuthState.SignedIn> {
        on<AuthAction.SignOut> { transition(state.toSignedOut()) }
    }
})
