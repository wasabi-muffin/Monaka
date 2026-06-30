package dev.gmvalentino.monaka.examples.checkout.cart

import dev.gmvalentino.monaka.core.Store
import dev.gmvalentino.monaka.dsl.store
import kotlinx.coroutines.CoroutineScope

class CartStore(
    stateMachine: CartStateMachine,
    scope: CoroutineScope,
    initialState: CartState? = null,
) : Store<CartState, CartAction, CartEffect> by store(
    stateMachine = stateMachine,
    initialState = initialState,
    scope = scope,
)
