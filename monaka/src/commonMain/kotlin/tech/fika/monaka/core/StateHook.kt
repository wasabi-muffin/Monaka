package tech.fika.monaka.core

/**
 * State-lifecycle hooks that can be triggered directly via [Store.triggerStateHook].
 *
 * Each value maps to the corresponding DSL hook registered via [tech.fika.monaka.dsl.StateBuilder]:
 * - [OnEnter] → `onEnter { }` — fired when the machine enters a state type.
 * - [OnExit]  → `onExit { }`  — fired when the machine exits a state type.
 * - [OnUpdate] → `onUpdate { }` — fired when the state value changes within the same type.
 *
 * Primarily intended for testing via `:monaka-test`'s `trigger(StateHook)` DSL.
 */
enum class StateHook {
    OnEnter,
    OnExit,
    OnUpdate,
}
