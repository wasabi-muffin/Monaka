package tech.fika.monaka.examples.checkout.auth

import tech.fika.monaka.core.State
import tech.fika.monaka.examples.checkout.data.User

sealed interface AuthState : State {
    data object SignedOut : AuthState
    data class SigningIn(val username: String, val password: String) : AuthState
    data class SignedIn(val user: User) : AuthState
    data class SignInFailed(val reason: String) : AuthState
}
