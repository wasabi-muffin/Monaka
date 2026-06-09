package dev.gmvalentino.monaka.core

/**
 * State-lifecycle hooks that can be triggered directly via [Store.triggerStateHook].
 *
 * Each subtype maps to the corresponding DSL hook registered via [dev.gmvalentino.monaka.dsl.StateBuilder]:
 * - [OnEnter]  → `onEnter { }` — fired when the machine enters a state type.
 * - [OnExit]   → `onExit { }`  — fired when the machine exits a state type.
 * - [OnUpdate] → `onUpdate { }` — fired when the state value changes within the same type.
 *   Carries [OnUpdate.previousState] so the handler's [dev.gmvalentino.monaka.scopes.StateUpdateScope.fromState]
 *   receives a meaningful baseline rather than the current state.
 *
 * Primarily intended for testing via `:monaka-test`'s `trigger(StateHook)` DSL.
 */
public sealed interface StateHook<out S> {
    /** Trigger the `onEnter` hook for the current state. */
    public data object OnEnter : StateHook<Nothing>
    /** Trigger the `onExit` hook for the current state. */
    public data object OnExit : StateHook<Nothing>

    /**
     * Trigger the `onUpdate { }` handler for the current state, using [previousState] as the
     * baseline passed to [dev.gmvalentino.monaka.scopes.StateUpdateScope.fromState].
     *
     * Pass the state value that should represent the "before" snapshot — typically the value
     * the state held before the most recent same-type transition.
     */
    public data class OnUpdate<out S>(public val previousState: S) : StateHook<S>
}
