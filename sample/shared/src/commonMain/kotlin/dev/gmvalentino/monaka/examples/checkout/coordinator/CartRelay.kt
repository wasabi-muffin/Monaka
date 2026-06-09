package dev.gmvalentino.monaka.examples.checkout.coordinator

import dev.gmvalentino.monaka.examples.checkout.cart.CartAction
import dev.gmvalentino.monaka.examples.checkout.cart.CartEffect
import dev.gmvalentino.monaka.examples.checkout.cart.CartState
import dev.gmvalentino.monaka.examples.checkout.cart.CartStore
import dev.gmvalentino.monaka.examples.checkout.checkout.CheckoutAction
import dev.gmvalentino.monaka.examples.checkout.checkout.CheckoutStore
import dev.gmvalentino.monaka.relay.Relay
import dev.gmvalentino.monaka.relay.relay

// Cart → Checkout: keep the order summary in sync when the cart changes.
object CartRelay : Relay<CartState, CartAction, CartEffect> by relay(from = CartStore::class, builder = {
    effect<CartEffect.CartChanged> {
        dispatch(CheckoutStore::class, CheckoutAction.SyncCart(event.items, event.total))
    }
})
