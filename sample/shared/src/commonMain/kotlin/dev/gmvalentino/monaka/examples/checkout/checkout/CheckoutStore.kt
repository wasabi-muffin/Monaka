package dev.gmvalentino.monaka.examples.checkout.checkout

import dev.gmvalentino.monaka.core.Store
import dev.gmvalentino.monaka.dsl.store
import kotlinx.coroutines.CoroutineScope

class CheckoutStore(
    stateMachine: CheckoutStateMachine,
    scope: CoroutineScope,
    initialState: CheckoutState? = null,
) : Store<CheckoutState, CheckoutAction, CheckoutEffect> by store(
    stateMachine = stateMachine,
    initialState = initialState,
    scope = scope,
)
