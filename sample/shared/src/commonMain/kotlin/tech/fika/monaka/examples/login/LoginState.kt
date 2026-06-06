package tech.fika.monaka.examples.login

import tech.fika.monaka.core.State
import tech.fika.monaka.core.Transition

@Transition
sealed interface LoginState : State {
    @Transition(Typing::class)
    data object Idle : LoginState

    @Transition(Submitting::class)
    data class Typing(
        val username: String,
        val password: String,
    ) : LoginState {
        val isValid: Boolean get() = username.isNotBlank() && password.isNotBlank()
    }

    @Transition(Typing::class, Error::class, Authenticated::class)
    data class Submitting(
        val username: String,
        val password: String,
    ) : LoginState

    data class Authenticated(val username: String) : LoginState

    @Transition(Submitting::class)
    data class Error(
        val username: String,
        val password: String,
        val message: String,
    ) : LoginState
}
