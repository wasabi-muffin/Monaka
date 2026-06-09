package dev.gmvalentino.monaka.examples.checkout.checkout

import dev.gmvalentino.monaka.core.Action
import dev.gmvalentino.monaka.examples.checkout.data.CartItem

sealed interface CheckoutAction : Action {
    /** Dispatched externally to start the checkout flow. */
    data class Begin(val userId: String, val items: List<CartItem>, val total: Double) : CheckoutAction

    /** Forwarded by the bridge when the cart changes mid-review. */
    data class SyncCart(val items: List<CartItem>, val total: Double) : CheckoutAction

    data object Confirm : CheckoutAction

    data class PaymentSucceeded(val orderId: String) : CheckoutAction
    data class PaymentFailed(val reason: String) : CheckoutAction

    data object RetryPayment : CheckoutAction

    /** Dispatched by the bridge when the user signs out. */
    data object Cancel : CheckoutAction
}
