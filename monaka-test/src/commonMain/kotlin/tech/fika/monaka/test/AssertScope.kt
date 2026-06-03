package tech.fika.monaka.test

import app.cash.turbine.ReceiveTurbine
import tech.fika.monaka.core.Action as ActionMarker
import tech.fika.monaka.core.Effect as EffectMarker
import tech.fika.monaka.core.State as StateMarker

/**
 * Receiver for assertions inside a [trigger] block.
 *
 * Each `expect*` call consumes from one of three independent streams recorded
 * from the underlying [tech.fika.monaka.core.Store]:
 * - **states** — every distinct state emitted after the trigger.
 * - **effects** — every side effect emitted after the trigger.
 * - **actions** — every action dispatched by a handler (via `ActionScope.dispatch`
 *   or from inside `launch { }`). Test-initiated triggers are filtered out.
 */
class AssertScope<S : StateMarker, A : ActionMarker, E : EffectMarker> internal constructor(
    @PublishedApi internal val states: ReceiveTurbine<S>,
    internal val effects: ReceiveTurbine<E>,
    internal val actions: ReceiveTurbine<A>,
) {
    /**
     * Await the next state and assert it matches [T] and the optional [predicate].
     */
    suspend inline fun <reified T : S> expectState(noinline predicate: (T) -> Boolean = { true }) {
        val item = states.awaitItem()
        check(item is T) { "Expected state of type ${T::class.simpleName}, got $item" }
        check(predicate(item)) { "State $item did not match predicate" }
    }

    /**
     * Await the next effect and assert equality with [effect].
     */
    suspend fun expectEffect(effect: E) {
        val item = effects.awaitItem()
        check(item == effect) { "Expected effect $effect, got $item" }
    }

    /**
     * Assert that no effect has been emitted yet.
     */
    suspend fun expectNoEffects() {
        effects.expectNoEvents()
    }

    /**
     * Await the next handler-initiated action and assert equality with [action].
     */
    suspend fun expectAction(action: A) {
        val item = actions.awaitItem()
        check(item == action) { "Expected dispatched action $action, got $item" }
    }

    /**
     * Assert that no handler-initiated action has been emitted yet.
     */
    suspend fun expectNoAction() {
        actions.expectNoEvents()
    }
}
