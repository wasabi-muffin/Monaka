package tech.fika.monaka.examples.checkout.auth

import tech.fika.monaka.core.State
import tech.fika.monaka.core.Transition
import tech.fika.monaka.examples.checkout.data.User

@Transition
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
