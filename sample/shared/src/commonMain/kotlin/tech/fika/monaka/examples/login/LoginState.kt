package tech.fika.monaka.examples.login

import tech.fika.monaka.core.State

sealed interface LoginState : State {
    data object Idle : LoginState

    data class Typing(
        val username: String,
        val password: String,
    ) : LoginState {
        val isValid: Boolean get() = username.isNotBlank() && password.isNotBlank()
    }

    data class Submitting(
        val username: String,
        val password: String,
    ) : LoginState

    data class Authenticated(val username: String) : LoginState

    data class Error(
        val username: String,
        val password: String,
        val message: String,
    ) : LoginState
}
