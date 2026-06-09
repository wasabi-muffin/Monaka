package dev.gmvalentino.monaka.examples.checkout.checkout

import dev.gmvalentino.monaka.core.State
import dev.gmvalentino.monaka.core.Transition
import dev.gmvalentino.monaka.examples.checkout.data.CartItem

@Transition
sealed interface CheckoutState : State {
    @Transition(ReviewingOrder::class)
    data object Idle : CheckoutState

    @Transition(ProcessingPayment::class, Idle::class)
    data class ReviewingOrder(
        val userId: String,
        val items: List<CartItem>,
        val total: Double,
    ) : CheckoutState

    @Transition(Done::class, PaymentFailed::class)
    data class ProcessingPayment(
        val userId: String,
        val items: List<CartItem>,
        val total: Double,
    ) : CheckoutState

    data class Done(val orderId: String) : CheckoutState

    @Transition(ProcessingPayment::class, Idle::class)
    data class PaymentFailed(
        val reason: String,
        val userId: String,
        val items: List<CartItem>,
        val total: Double,
    ) : CheckoutState
}
