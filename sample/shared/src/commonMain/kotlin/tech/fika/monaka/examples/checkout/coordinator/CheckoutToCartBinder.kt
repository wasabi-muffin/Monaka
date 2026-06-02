package tech.fika.monaka.examples.checkout.coordinator

import tech.fika.monaka.binder.Binder
import tech.fika.monaka.binder.binder
import tech.fika.monaka.examples.checkout.cart.CartAction
import tech.fika.monaka.examples.checkout.cart.CartStore
import tech.fika.monaka.examples.checkout.checkout.CheckoutAction
import tech.fika.monaka.examples.checkout.checkout.CheckoutEffect
import tech.fika.monaka.examples.checkout.checkout.CheckoutState
import tech.fika.monaka.examples.checkout.checkout.CheckoutStore

// Checkout → Cart: clear cart once an order is placed
object CheckoutToCartBinder : Binder<CheckoutState, CheckoutAction, CheckoutEffect, CartAction> by binder(
    from = CheckoutStore::class,
    to = CartStore::class,
    builder = {
        bindState<CheckoutState.Done> { CartAction.Clear }
    }
)
