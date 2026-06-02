package tech.fika.monaka.dsl

/**
 * DSL marker that prevents implicit receiver leakage across nested DSL scopes.
 *
 * Applied to [StateMachineBuilder] and [StateBuilder] so that, for example,
 * calling `initialState()` from inside a `state { }` block is a compile error.
 */
@DslMarker
annotation class MonakaDsl
