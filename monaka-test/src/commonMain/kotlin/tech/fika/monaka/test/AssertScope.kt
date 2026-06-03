package tech.fika.monaka.test

import app.cash.turbine.ReceiveTurbine
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
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
 *   or from inside `task { }`). Test-initiated triggers are filtered out.
 */
@MonakaTestDsl
class AssertScope<State : StateMarker, Action : ActionMarker, Effect : EffectMarker> internal constructor(
    @PublishedApi internal val states: ReceiveTurbine<State>,
    @PublishedApi internal val effects: ReceiveTurbine<Effect>,
    @PublishedApi internal val actions: ReceiveTurbine<Action>,
) {
    inner class StateScope<T : State>(val state: T)
    inner class ActionScope<T : Action>(val action: T)
    inner class EffectScope<T : Effect>(val effect: T)

    // ── State ─────────────────────────────────────────────────────────────────

    /** Await the next state and assert equality with [state]. */
    suspend fun expectState(state: State) {
        states.awaitItem() shouldBe state
    }

    /** Await the next state and assert it matches [T] and the optional [predicate]. */
    suspend inline fun <reified T : State> expectState(noinline predicate: StateScope<T>.() -> Boolean = { true }) {
        val item = states.awaitItem()
        val state = item.shouldBeInstanceOf<T>()
        withClue("State $state did not match predicate") { StateScope(state).predicate() shouldBe true }
    }

    /** Consume the next state without asserting anything about it. */
    suspend fun skipState() {
        states.awaitItem()
    }

    // ── Effect ────────────────────────────────────────────────────────────────

    /** Await the next effect and assert equality with [effect]. */
    suspend fun expectEffect(effect: Effect) {
        effects.awaitItem() shouldBe effect
    }

    /** Await the next effect and assert it matches [T] and the optional [predicate]. */
    suspend inline fun <reified T : Effect> expectEffect(noinline predicate: EffectScope<T>.() -> Boolean = { true }) {
        val item = effects.awaitItem()
        val effect = item.shouldBeInstanceOf<T>()
        withClue("Effect $effect did not match predicate") { EffectScope(effect).predicate() shouldBe true }
    }

    /** Consume the next effect without asserting anything about it. */
    suspend fun skipEffect() {
        effects.awaitItem()
    }

    /** Assert that no effect has been emitted yet. */
    suspend fun expectNoEffects() {
        effects.expectNoEvents()
    }

    // ── Action ────────────────────────────────────────────────────────────────

    /** Await the next handler-initiated action and assert equality with [action]. */
    suspend fun expectAction(action: Action) {
        actions.awaitItem() shouldBe action
    }

    /** Await the next handler-initiated action and assert it matches [T] and the optional [predicate]. */
    suspend inline fun <reified T : Action> expectAction(noinline predicate: ActionScope<T>.() -> Boolean = { true }) {
        val item = actions.awaitItem()
        val action = item.shouldBeInstanceOf<T>()
        withClue("Action $action did not match predicate") { ActionScope(action).predicate() shouldBe true }
    }

    /** Consume the next handler-initiated action without asserting anything about it. */
    suspend fun skipAction() {
        actions.awaitItem()
    }

    /** Assert that no handler-initiated action has been emitted yet. */
    suspend fun expectNoAction() {
        actions.expectNoEvents()
    }
}
