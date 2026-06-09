package dev.gmvalentino.monaka.examples.checkout.cart

import dev.gmvalentino.monaka.core.Effect
import dev.gmvalentino.monaka.examples.checkout.data.CartItem

sealed interface CartEffect : Effect {
    /**
     * Emitted whenever cart contents change while a user is signed in.
     * Observed by [dev.gmvalentino.monaka.examples.checkout.checkout.CheckoutStateMachine] to keep the order summary in sync.
     */
    data class CartChanged(
        val userId: String,
        val items: List<CartItem>,
        val total: Double,
    ) : CartEffect
}
