package tech.fika.monaka.examples.checkout.cart

import tech.fika.monaka.core.State
import tech.fika.monaka.examples.checkout.data.CartItem

sealed interface CartState : State {
    /** No user is signed in, or cart has been cleared. */
    data object Empty : CartState

    /** Cart is loading for a user (immediately after sign-in). */
    data class Loading(val userId: String) : CartState

    data class WithItems(
        val userId: String,
        val items: List<CartItem>,
    ) : CartState {
        val total: Double get() = items.sumOf { it.subtotal }
        val isEmpty: Boolean get() = items.isEmpty()
    }
}
