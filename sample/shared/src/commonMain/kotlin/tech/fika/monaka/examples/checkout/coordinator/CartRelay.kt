package tech.fika.monaka.examples.checkout.coordinator

import tech.fika.monaka.examples.checkout.cart.CartAction
import tech.fika.monaka.examples.checkout.cart.CartEffect
import tech.fika.monaka.examples.checkout.cart.CartState
import tech.fika.monaka.examples.checkout.cart.CartStore
import tech.fika.monaka.examples.checkout.checkout.CheckoutAction
import tech.fika.monaka.examples.checkout.checkout.CheckoutStore
import tech.fika.monaka.relay.Relay
import tech.fika.monaka.relay.relay

// Cart → Checkout: keep the order summary in sync when the cart changes.
object CartRelay : Relay<CartState, CartAction, CartEffect> by relay(from = CartStore::class, builder = {
    effect<CartEffect.CartChanged> {
        dispatch<CheckoutStore>(CheckoutAction.SyncCart(event.items, event.total))
    }
})
