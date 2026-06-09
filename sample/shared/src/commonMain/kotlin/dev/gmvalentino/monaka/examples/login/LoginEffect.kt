package dev.gmvalentino.monaka.examples.login

import dev.gmvalentino.monaka.core.Effect

sealed interface LoginEffect : Effect {
    data class ShowValidationError(val message: String) : LoginEffect
    data object NavigateToHome : LoginEffect
    data object NavigateToLogin : LoginEffect
}
