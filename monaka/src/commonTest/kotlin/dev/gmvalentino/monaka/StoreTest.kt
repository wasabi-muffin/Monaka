package dev.gmvalentino.monaka

import app.cash.turbine.test
import dev.gmvalentino.monaka.core.Action
import dev.gmvalentino.monaka.core.Effect
import dev.gmvalentino.monaka.core.State
import dev.gmvalentino.monaka.dsl.store
import dev.gmvalentino.monaka.plugin.plugin
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private sealed interface SState : State {
    data object Idle : SState
    data object Active : SState
    data object Done : SState
}

private sealed interface SAction : Action {
    data object Go : SAction
    data object Finish : SAction
    data object Unknown : SAction
}

private sealed interface SEffect : Effect {
    data object Ping : SEffect
    data object Pong : SEffect
}

class StoreTest {

    @Test
    fun initialState_emittedOnFirstCollection() = runTest {
        val store = store<SState, SAction, SEffect>(scope = backgroundScope) {
            initialState(SState.Idle)
        }
        store.state.test {
            assertEquals(SState.Idle, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun dispatch_causesStateTransition() = runTest {
        val store = store<SState, SAction, SEffect>(scope = backgroundScope) {
            initialState(SState.Idle)
            state<SState.Idle> {
                on<SAction.Go> { transition(SState.Active) }
            }
        }
        store.state.test {
            awaitItem()
            store.dispatch(SAction.Go)
            assertEquals(SState.Active, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun sideEffectOnly_doesNotChangeState() = runTest {
        val effects = mutableListOf<SEffect>()
        val store = store<SState, SAction, SEffect>(
            scope = backgroundScope,
            plugins = listOf(plugin { onEffect { effects += effect as SEffect } }),
        ) {
            initialState(SState.Idle)
            state<SState.Idle> {
                on<SAction.Go> { sideEffect(SEffect.Ping) }
            }
        }
        store.state.test {
            awaitItem()
            store.dispatch(SAction.Go)
            delay(1)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(SState.Idle, store.state.value)
        assertEquals<List<SEffect>>(listOf(SEffect.Ping), effects)
    }

    @Test
    fun transitionAndEffect_bothApplied() = runTest {
        val effects = mutableListOf<SEffect>()
        val store = store<SState, SAction, SEffect>(
            scope = backgroundScope,
            plugins = listOf(plugin { onEffect { effects += effect as SEffect } }),
        ) {
            initialState(SState.Idle)
            state<SState.Idle> {
                on<SAction.Go> {
                    transition(SState.Active)
                    sideEffect(SEffect.Ping)
                }
            }
        }
        store.state.test {
            awaitItem()
            store.dispatch(SAction.Go)
            assertEquals(SState.Active, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals<List<SEffect>>(listOf(SEffect.Ping), effects)
    }

    @Test
    fun multipleEffects_emittedInDeclarationOrder() = runTest {
        val effects = mutableListOf<SEffect>()
        val store = store<SState, SAction, SEffect>(
            scope = backgroundScope,
            plugins = listOf(plugin { onEffect { effects += effect as SEffect } }),
        ) {
            initialState(SState.Idle)
            state<SState.Idle> {
                on<SAction.Go> { sideEffect(SEffect.Ping, SEffect.Pong) }
            }
        }
        store.state.test {
            awaitItem()
            store.dispatch(SAction.Go)
            delay(1)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals<List<SEffect>>(listOf(SEffect.Ping, SEffect.Pong), effects)
    }

    @Test
    fun multipleEffectsViaAccumulation_emittedInCallOrder() = runTest {
        val effects = mutableListOf<SEffect>()
        val store = store<SState, SAction, SEffect>(
            scope = backgroundScope,
            plugins = listOf(plugin { onEffect { effects += effect as SEffect } }),
        ) {
            initialState(SState.Idle)
            state<SState.Idle> {
                on<SAction.Go> {
                    sideEffect(SEffect.Ping)
                    sideEffect(SEffect.Pong)
                }
            }
        }
        store.state.test {
            awaitItem()
            store.dispatch(SAction.Go)
            delay(1)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals<List<SEffect>>(listOf(SEffect.Ping, SEffect.Pong), effects)
    }

    @Test
    fun unhandledAction_stateUnchanged() = runTest {
        val store = store<SState, SAction, SEffect>(scope = backgroundScope) {
            initialState(SState.Idle)
        }
        store.state.test {
            awaitItem()
            store.dispatch(SAction.Unknown)
            delay(1)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(SState.Idle, store.state.value)
    }

    @Test
    fun emptyHandler_stateUnchanged() = runTest {
        val store = store<SState, SAction, SEffect>(scope = backgroundScope) {
            initialState(SState.Idle)
            state<SState.Idle> {
                on<SAction.Go> { /* intentional no-op */ }
            }
        }
        store.state.test {
            awaitItem()
            store.dispatch(SAction.Go)
            delay(1)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(SState.Idle, store.state.value)
    }

    @Test
    fun sequentialActions_processedInOrder() = runTest {
        val store = store<SState, SAction, SEffect>(scope = backgroundScope) {
            initialState(SState.Idle)
            state<SState.Idle> {
                on<SAction.Go> { transition(SState.Active) }
            }
            state<SState.Active> {
                on<SAction.Finish> { transition(SState.Done) }
            }
        }
        store.state.test {
            awaitItem() // Idle
            store.dispatch(SAction.Go)
            assertEquals(SState.Active, awaitItem())
            store.dispatch(SAction.Finish)
            assertEquals(SState.Done, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun isActive_trueInitially_falseAfterStop() = runTest {
        val store = store<SState, SAction, SEffect>(scope = backgroundScope) {
            initialState(SState.Idle)
        }
        assertTrue(store.isActive)
        store.stop()
        assertFalse(store.isActive)
    }

    @Test
    fun stop_dropsSubsequentDispatches() = runTest {
        val store = store<SState, SAction, SEffect>(scope = backgroundScope) {
            initialState(SState.Idle)
            state<SState.Idle> {
                on<SAction.Go> { transition(SState.Active) }
            }
        }
        store.state.test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        store.stop()
        assertFalse(store.isActive)
        store.dispatch(SAction.Go)
        assertEquals(SState.Idle, store.state.value)
    }
}
