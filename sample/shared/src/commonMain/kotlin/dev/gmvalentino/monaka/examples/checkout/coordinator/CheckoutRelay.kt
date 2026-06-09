package dev.gmvalentino.monaka.examples.checkout.coordinator

import dev.gmvalentino.monaka.examples.checkout.cart.CartAction
import dev.gmvalentino.monaka.examples.checkout.cart.CartStore
import dev.gmvalentino.monaka.examples.checkout.checkout.CheckoutAction
import dev.gmvalentino.monaka.examples.checkout.checkout.CheckoutEffect
import dev.gmvalentino.monaka.examples.checkout.checkout.CheckoutState
import dev.gmvalentino.monaka.examples.checkout.checkout.CheckoutStore
import dev.gmvalentino.monaka.relay.Relay
import dev.gmvalentino.monaka.relay.relay

// Checkout → Cart: clear the cart once an order is placed.
// Uses the KClass form of dispatch, which type-checks the action against the target store.
object CheckoutRelay : Relay<CheckoutState, CheckoutAction, CheckoutEffect> by relay(from = CheckoutStore::class, builder = {
    state<CheckoutState.Done> {
        dispatch(CartStore::class, CartAction.Clear)
    }
})
