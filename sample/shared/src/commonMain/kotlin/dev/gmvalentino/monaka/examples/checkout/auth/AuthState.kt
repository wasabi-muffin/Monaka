package dev.gmvalentino.monaka.examples.checkout.auth

import dev.gmvalentino.monaka.core.SelfTransition
import dev.gmvalentino.monaka.core.State
import dev.gmvalentino.monaka.core.Transition
import dev.gmvalentino.monaka.examples.checkout.data.User

@SelfTransition
sealed interface AuthState : State {
    @Transition(SigningIn::class)
    data object SignedOut : AuthState

    @Transition(SignedIn::class, SignInFailed::class, SignedOut::class)
    data class SigningIn(val username: String, val password: String) : AuthState

    @Transition(SignedOut::class)
    data class SignedIn(val user: User) : AuthState

    @Transition(SigningIn::class, SignedOut::class)
    data class SignInFailed(val reason: String) : AuthState
}
