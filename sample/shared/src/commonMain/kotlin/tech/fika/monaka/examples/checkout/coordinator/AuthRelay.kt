package tech.fika.monaka.examples.checkout.coordinator

import tech.fika.monaka.examples.checkout.auth.AuthAction
import tech.fika.monaka.examples.checkout.auth.AuthEffect
import tech.fika.monaka.examples.checkout.auth.AuthState
import tech.fika.monaka.examples.checkout.auth.AuthStore
import tech.fika.monaka.examples.checkout.cart.CartAction
import tech.fika.monaka.examples.checkout.cart.CartStore
import tech.fika.monaka.examples.checkout.checkout.CheckoutAction
import tech.fika.monaka.examples.checkout.checkout.CheckoutStore
import tech.fika.monaka.relay.Relay
import tech.fika.monaka.relay.relay

// Auth → Cart + Checkout: load the cart on sign-in; clear the cart and cancel
// any in-progress checkout on sign-out. One relay fans out to multiple targets.
object AuthRelay : Relay<AuthState, AuthAction, AuthEffect> by relay(from = AuthStore::class, builder = {
    state<AuthState.SignedIn> {
        dispatch<CartStore>(CartAction.LoadForUser(event.user.id))
    }
    state<AuthState.SignedOut> {
        dispatch<CartStore>(CartAction.Clear)
        dispatch<CheckoutStore>(CheckoutAction.Cancel)
    }
})
