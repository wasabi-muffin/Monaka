package tech.fika.monaka.dsl

/**
 * DSL marker that prevents implicit receiver leakage across nested DSL scopes.
 *
 * Applied to all DSL receiver classes — [StateMachineBuilder], [StateBuilder], the handler
 * scope hierarchy ([tech.fika.monaka.scopes.HandlerScope] and its subclasses), and
 * [tech.fika.monaka.scopes.TaskScope] — so that:
 *
 * - Calling `initialState()` from inside a `state { }` block is a compile error.
 * - Calling `transition`, `sideEffect`, `reject`, `guard`, or `cancel` from inside a
 *   `task { }` block is a compile error, because [tech.fika.monaka.scopes.TaskScope]
 *   carries this marker and blocks the outer handler scope's implicit receiver.
 */
@DslMarker
annotation class MonakaDsl
