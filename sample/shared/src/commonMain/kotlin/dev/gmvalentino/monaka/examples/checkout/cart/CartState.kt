package dev.gmvalentino.monaka.examples.checkout.cart

import dev.gmvalentino.monaka.core.State
import dev.gmvalentino.monaka.core.Transition
import dev.gmvalentino.monaka.examples.checkout.data.CartItem

@Transition
sealed interface CartState : State {
    /** No user is signed in, or cart has been cleared. */
    @Transition(Loading::class)
    data object Empty : CartState

    /** Cart is loading for a user (immediately after sign-in). */
    @Transition(WithItems::class, Empty::class)
    data class Loading(val userId: String) : CartState

    @Transition(Empty::class)
    data class WithItems(
        val userId: String,
        val items: List<CartItem>,
    ) : CartState {
        val total: Double get() = items.sumOf { it.subtotal }
        val isEmpty: Boolean get() = items.isEmpty()
    }
}
