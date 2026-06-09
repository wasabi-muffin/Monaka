package dev.gmvalentino.monaka.examples.checkout.cart

import dev.gmvalentino.monaka.core.Action
import dev.gmvalentino.monaka.examples.checkout.data.CartItem

sealed interface CartAction : Action {
    /** Dispatched by the bridge when auth reaches SignedIn. */
    data class LoadForUser(val userId: String) : CartAction
    data class AddItem(val item: CartItem) : CartAction
    data class RemoveItem(val productId: String) : CartAction
    data class UpdateQuantity(val productId: String, val quantity: Int) : CartAction

    /** Dispatched by the bridge when the checkout order is placed. */
    data object Clear : CartAction
}
