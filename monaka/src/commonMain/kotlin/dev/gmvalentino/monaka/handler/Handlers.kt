package dev.gmvalentino.monaka.handler

import dev.gmvalentino.monaka.scopes.ActionScope
import dev.gmvalentino.monaka.scopes.ErrorScope
import dev.gmvalentino.monaka.scopes.LifecycleScope
import dev.gmvalentino.monaka.scopes.StateChangeScope
import dev.gmvalentino.monaka.scopes.StateUpdateScope

/**
 * Generic handler shape: a suspending lambda with [Scope] as receiver, returning [Unit].
 *
 * The handler records its outcome (transition, side effects, rejection) via the mutating
 * verbs on [Scope]. The runtime snapshots that state via
 * [dev.gmvalentino.monaka.dsl.consumeResult] after the lambda returns.
 */
public typealias Handler<Scope> = suspend Scope.() -> Unit

/**
 * Type-erased action handler stored in the handler registry.
 *
 * [dev.gmvalentino.monaka.dsl.HandlerScope] is the implicit receiver so that all handlers — whether
 * they use it or not — have access to [dev.gmvalentino.monaka.dsl.HandlerScope.dispatch] and
 * [dev.gmvalentino.monaka.dsl.HandlerScope.task].
 *
 * Inputs are erased to [Any]; casts inside the wrapper lambdas are safe by construction
 * (keyed on the exact runtime class of state and action).
 */
internal typealias ActionHandler<State, Action, Effect> =
    suspend ActionScope<State, Action, Effect, State, Action>.() -> Unit

/**
 * Type-erased state change hook stored in the hook registries.
 *
 * Receives the current state (erased to [Any]); the cast back to the concrete subtype is
 * inserted by the DSL builders at registration time and is safe by construction (keyed on
 * the exact runtime class of the state).
 */
internal typealias StateChangeHandler<State, Action, Effect> =
    suspend StateChangeScope<State, Action, Effect, State>.() -> Unit

/**
 * Type-erased *update* hook — fired when the state value changes but its type does not.
 *
 * Receives both the old and new states, erased to [Any].
 */
internal typealias StateUpdateHandler<State, Action, Effect> =
    suspend StateUpdateScope<State, Action, Effect, State>.() -> Unit

/**
 * Type-erased lifecycle event hook — fired when an application
 * [dev.gmvalentino.monaka.core.LifecycleEvent] is forwarded to the machine while the machine is
 * in the registered state.
 */
internal typealias LifecycleHandler<State, Action, Effect> =
    suspend LifecycleScope<State, Action, Effect, State>.() -> Unit

/**
 * Type-erased error recovery hook — fired when any handler or hook throws an exception
 * while the machine is in the registered state.
 *
 * Named `StateErrorHandler` (not `ErrorHandler`) to avoid collisions with common
 * user-defined error handler type names in import lists.
 */
internal typealias StateErrorHandler<State, Action, Effect> =
    suspend ErrorScope<State, Action, Effect, State>.() -> Unit
