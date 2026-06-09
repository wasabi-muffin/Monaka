package dev.gmvalentino.monaka.examples.checkout.coordinator

import dev.gmvalentino.monaka.examples.checkout.auth.AuthAction
import dev.gmvalentino.monaka.examples.checkout.auth.AuthEffect
import dev.gmvalentino.monaka.examples.checkout.auth.AuthState
import dev.gmvalentino.monaka.examples.checkout.auth.AuthStore
import dev.gmvalentino.monaka.examples.checkout.cart.CartAction
import dev.gmvalentino.monaka.examples.checkout.cart.CartStore
import dev.gmvalentino.monaka.examples.checkout.checkout.CheckoutAction
import dev.gmvalentino.monaka.examples.checkout.checkout.CheckoutStore
import dev.gmvalentino.monaka.relay.Relay
import dev.gmvalentino.monaka.relay.relay

// Auth → Cart + Checkout: load the cart on sign-in; clear the cart and cancel
// any in-progress checkout on sign-out. One relay fans out to multiple targets.
object AuthRelay : Relay<AuthState, AuthAction, AuthEffect> by relay(from = AuthStore::class, builder = {
    state<AuthState.SignedIn> {
        dispatch(target = CartStore::class, CartAction.LoadForUser(event.user.id))
    }
    state<AuthState.SignedOut> {
        dispatch(CartStore::class, CartAction.Clear)
        dispatch(CheckoutStore::class, CheckoutAction.Cancel)
    }
})
