package tech.fika.monaka.examples.login

import tech.fika.monaka.core.Effect

sealed interface LoginEffect : Effect {
    data class ShowValidationError(val message: String) : LoginEffect
    data object NavigateToHome : LoginEffect
    data object NavigateToLogin : LoginEffect
}
