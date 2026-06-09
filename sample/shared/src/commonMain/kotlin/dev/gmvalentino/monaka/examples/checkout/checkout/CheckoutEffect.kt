package dev.gmvalentino.monaka.examples.checkout.checkout

import dev.gmvalentino.monaka.core.Effect

sealed interface CheckoutEffect : Effect {
    data class OrderConfirmed(val orderId: String) : CheckoutEffect
    data class ShowPaymentError(val message: String) : CheckoutEffect
}
