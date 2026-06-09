package dev.gmvalentino.monaka.examples.checkout.checkout

import kotlinx.coroutines.CoroutineScope
import dev.gmvalentino.monaka.core.Store
import dev.gmvalentino.monaka.dsl.store

class CheckoutStore(
    stateMachine: CheckoutStateMachine,
    scope: CoroutineScope,
    initialState: CheckoutState? = null,
) : Store<CheckoutState, CheckoutAction, CheckoutEffect> by store(
    stateMachine = stateMachine,
    initialState = initialState,
    scope = scope,
)
