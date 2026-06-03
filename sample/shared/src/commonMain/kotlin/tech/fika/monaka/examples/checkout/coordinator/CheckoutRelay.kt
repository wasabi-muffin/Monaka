package tech.fika.monaka.examples.checkout.coordinator

import tech.fika.monaka.examples.checkout.cart.CartAction
import tech.fika.monaka.examples.checkout.cart.CartStore
import tech.fika.monaka.examples.checkout.checkout.CheckoutAction
import tech.fika.monaka.examples.checkout.checkout.CheckoutEffect
import tech.fika.monaka.examples.checkout.checkout.CheckoutState
import tech.fika.monaka.examples.checkout.checkout.CheckoutStore
import tech.fika.monaka.relay.Relay
import tech.fika.monaka.relay.relay

// Checkout → Cart: clear the cart once an order is placed.
// Uses the KClass form of dispatch, which type-checks the action against the target store.
object CheckoutRelay : Relay<CheckoutState, CheckoutAction, CheckoutEffect> by relay(from = CheckoutStore::class, builder = {
    state<CheckoutState.Done> {
        dispatch(CartStore::class, CartAction.Clear)
    }
})
