package dev.gmvalentino.monaka.examples.checkout.auth

import dev.gmvalentino.monaka.core.Action

sealed interface AuthAction : Action {
    data class Attempt(val username: String, val password: String) : AuthAction
    data object SignOut : AuthAction
}
