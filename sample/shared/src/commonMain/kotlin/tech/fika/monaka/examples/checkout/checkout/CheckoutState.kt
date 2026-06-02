package tech.fika.monaka.examples.checkout.checkout

import tech.fika.monaka.core.State
import tech.fika.monaka.examples.checkout.data.CartItem

sealed interface CheckoutState : State {
    data object Idle : CheckoutState

    data class ReviewingOrder(
        val userId: String,
        val items: List<CartItem>,
        val total: Double,
    ) : CheckoutState

    data class ProcessingPayment(
        val userId: String,
        val items: List<CartItem>,
        val total: Double,
    ) : CheckoutState

    data class Done(val orderId: String) : CheckoutState

    data class PaymentFailed(
        val reason: String,
        val userId: String,
        val items: List<CartItem>,
        val total: Double,
    ) : CheckoutState
}
