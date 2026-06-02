package tech.fika.monaka.handler

import tech.fika.monaka.dsl.ActionScope
import tech.fika.monaka.dsl.ErrorScope
import tech.fika.monaka.dsl.LifecycleScope
import tech.fika.monaka.dsl.StateChangeScope
import tech.fika.monaka.dsl.StateUpdateScope

/**
 * Generic handler shape: a suspending lambda with [Scope] as receiver, returning [Unit].
 *
 * The handler records its outcome (transition, side effects, rejection) via the mutating
 * verbs on [Scope]. The runtime snapshots that state via
 * [tech.fika.monaka.dsl.consumeResult] after the lambda returns.
 */
typealias Handler<Scope> = suspend Scope.() -> Unit

/**
 * Type-erased action handler stored in the handler registry.
 *
 * [tech.fika.monaka.dsl.HandlerScope] is the implicit receiver so that all handlers — whether
 * they use it or not — have access to [tech.fika.monaka.dsl.HandlerScope.dispatch] and
 * [tech.fika.monaka.dsl.HandlerScope.launch].
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
 * [tech.fika.monaka.core.LifecycleEvent] is forwarded to the machine while the machine is
 * in the registered state.
 */
internal typealias LifecycleHandler<State, Action, Effect> =
        suspend LifecycleScope<State, Action, Effect, State>.() -> Unit

/**
 * Type-erased error recovery hook — fired when any handler or hook throws an exception
 * while the machine is in the registered state.
 *
 * Named `StateErrorHandler` (not `ErrorHandler`) to avoid shadowing
 * [tech.fika.monaka.error.ErrorMapper] in import lists.
 */
internal typealias StateErrorHandler<State, Action, Effect> =
        suspend ErrorScope<State, Action, Effect, State>.() -> Unit
