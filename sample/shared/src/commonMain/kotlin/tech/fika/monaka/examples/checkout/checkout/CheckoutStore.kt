package tech.fika.monaka.examples.checkout.checkout

import kotlinx.coroutines.CoroutineScope
import tech.fika.monaka.core.Store
import tech.fika.monaka.dsl.store

class CheckoutStore(
    stateMachine: CheckoutStateMachine,
    scope: CoroutineScope,
    initialState: CheckoutState? = null,
) : Store<CheckoutState, CheckoutAction, CheckoutEffect> by store(
    stateMachine = stateMachine,
    initialState = initialState,
    scope = scope,
)
