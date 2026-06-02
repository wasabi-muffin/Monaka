package tech.fika.monaka.examples.checkout.coordinator

import tech.fika.monaka.binder.Binder
import tech.fika.monaka.binder.binder
import tech.fika.monaka.examples.checkout.auth.AuthAction
import tech.fika.monaka.examples.checkout.auth.AuthEffect
import tech.fika.monaka.examples.checkout.auth.AuthState
import tech.fika.monaka.examples.checkout.auth.AuthStore
import tech.fika.monaka.examples.checkout.cart.CartAction
import tech.fika.monaka.examples.checkout.cart.CartStore

// Auth → Cart: load on sign-in, clear on sign-out
object AuthToCartBinder : Binder<AuthState, AuthAction, AuthEffect, CartAction> by binder(
    from = AuthStore::class,
    to = CartStore::class,
    builder = {
        bindState<AuthState.SignedIn> { CartAction.LoadForUser(user.id) }
        bindState<AuthState.SignedOut> { CartAction.Clear }
    }
)
