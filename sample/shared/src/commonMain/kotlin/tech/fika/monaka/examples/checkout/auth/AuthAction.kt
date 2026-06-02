package tech.fika.monaka.examples.checkout.auth

import tech.fika.monaka.core.Action

sealed interface AuthAction : Action {
    data class Attempt(val username: String, val password: String) : AuthAction
    data object SignOut : AuthAction
}
