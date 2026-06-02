package tech.fika.monaka.examples.checkout.cart

import kotlinx.coroutines.CoroutineScope
import tech.fika.monaka.core.Store
import tech.fika.monaka.dsl.store

class CartStore(
    stateMachine: CartStateMachine,
    scope: CoroutineScope,
    initialState: CartState? = null,
) : Store<CartState, CartAction, CartEffect> by store(
    stateMachine = stateMachine,
    initialState = initialState,
    scope = scope,
)
