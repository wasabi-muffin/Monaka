package tech.fika.monaka.examples.checkout.auth

import kotlinx.coroutines.CoroutineScope
import tech.fika.monaka.core.Store
import tech.fika.monaka.dsl.store

class AuthStore(
    stateMachine: AuthStateMachine,
    scope: CoroutineScope,
    initialState: AuthState? = null,
) : Store<AuthState, AuthAction, AuthEffect> by store(
    stateMachine = stateMachine,
    initialState = initialState,
    scope = scope,
)
