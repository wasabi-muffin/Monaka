package tech.fika.monaka.examples.checkout.coordinator

import tech.fika.monaka.binder.Binder
import tech.fika.monaka.binder.binder
import tech.fika.monaka.examples.checkout.cart.CartAction
import tech.fika.monaka.examples.checkout.cart.CartEffect
import tech.fika.monaka.examples.checkout.cart.CartState
import tech.fika.monaka.examples.checkout.cart.CartStore
import tech.fika.monaka.examples.checkout.checkout.CheckoutAction
import tech.fika.monaka.examples.checkout.checkout.CheckoutStore

// Cart (effects) → Checkout: keep order summary in sync when cart changes
object CartToCheckoutBinder : Binder<CartState, CartAction, CartEffect, CheckoutAction> by binder(
    from = CartStore::class,
    to = CheckoutStore::class,
    builder = {
        bindEffect<CartEffect.CartChanged> { CheckoutAction.SyncCart(items, total) }
    }
)
