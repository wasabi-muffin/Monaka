package dev.gmvalentino.monaka.examples.checkout.auth

import kotlinx.coroutines.CoroutineScope
import dev.gmvalentino.monaka.core.Store
import dev.gmvalentino.monaka.dsl.store

class AuthStore(
    stateMachine: AuthStateMachine,
    scope: CoroutineScope,
    initialState: AuthState? = null,
) : Store<AuthState, AuthAction, AuthEffect> by store(
    stateMachine = stateMachine,
    initialState = initialState,
    scope = scope,
)
