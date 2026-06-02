package tech.fika.monaka.examples.checkout.checkout

import tech.fika.monaka.core.Effect

sealed interface CheckoutEffect : Effect {
    data class OrderConfirmed(val orderId: String) : CheckoutEffect
    data class ShowPaymentError(val message: String) : CheckoutEffect
}
