package tech.fika.monaka.examples.checkout.cart

import tech.fika.monaka.core.Effect
import tech.fika.monaka.examples.checkout.data.CartItem

sealed interface CartEffect : Effect {
    /**
     * Emitted whenever cart contents change while a user is signed in.
     * Observed by [tech.fika.monaka.examples.checkout.checkout.CheckoutStateMachine] to keep the order summary in sync.
     */
    data class CartChanged(
        val userId: String,
        val items: List<CartItem>,
        val total: Double,
    ) : CartEffect
}
