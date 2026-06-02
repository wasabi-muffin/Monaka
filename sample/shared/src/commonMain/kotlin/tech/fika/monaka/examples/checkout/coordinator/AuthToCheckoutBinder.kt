package tech.fika.monaka.examples.checkout.coordinator

import tech.fika.monaka.binder.Binder
import tech.fika.monaka.binder.binder
import tech.fika.monaka.examples.checkout.auth.AuthAction
import tech.fika.monaka.examples.checkout.auth.AuthEffect
import tech.fika.monaka.examples.checkout.auth.AuthState
import tech.fika.monaka.examples.checkout.auth.AuthStore
import tech.fika.monaka.examples.checkout.checkout.CheckoutAction
import tech.fika.monaka.examples.checkout.checkout.CheckoutStore

// Auth → Checkout: cancel in-progress checkout on sign-out
object AuthToCheckoutBinder : Binder<AuthState, AuthAction, AuthEffect, CheckoutAction> by binder(
    from = AuthStore::class,
    to = CheckoutStore::class,
    builder = {
        bindState<AuthState.SignedOut> { CheckoutAction.Cancel }
    }
)
