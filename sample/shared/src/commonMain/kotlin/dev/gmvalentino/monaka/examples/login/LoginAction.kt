package dev.gmvalentino.monaka.examples.login

import dev.gmvalentino.monaka.core.Action

sealed interface LoginAction : Action {
    data class UpdateCredentials(val username: String, val password: String) : LoginAction
    data object Submit : LoginAction
    data object Retry : LoginAction
    data object Logout : LoginAction
}
