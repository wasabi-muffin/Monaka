package dev.gmvalentino.monaka.dsl

/**
 * DSL marker that prevents implicit receiver leakage across nested DSL scopes.
 *
 * Applied to all DSL receiver classes — [StateMachineBuilder], [StateBuilder], the handler
 * scope hierarchy ([dev.gmvalentino.monaka.scopes.HandlerScope] and its subclasses), and
 * [dev.gmvalentino.monaka.scopes.TaskScope] — so that:
 *
 * - Calling `initialState()` from inside a `state { }` block is a compile error.
 * - Calling `transition`, `sideEffect`, `reject`, `guard`, or `cancel` from inside a
 *   `task { }` block is a compile error, because [dev.gmvalentino.monaka.scopes.TaskScope]
 *   carries this marker and blocks the outer handler scope's implicit receiver.
 */
@DslMarker
public annotation class MonakaDsl
