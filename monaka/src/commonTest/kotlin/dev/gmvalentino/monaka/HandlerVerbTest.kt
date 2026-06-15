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
import kotlin.test.assertTrue

private sealed interface VState : State {
    data object Idle : VState
    data object Active : VState
}

private sealed interface VAction : Action {
    data object Go : VAction
    data object Dispatch : VAction
    data object Dispatched : VAction
}

private sealed interface VEffect : Effect {
    data object Ping : VEffect
    data object Pong : VEffect
}

class HandlerVerbTest {

    @Test
    fun reject_isTerminal_stateUnchanged() = runTest {
        val store = store<VState, VAction, VEffect>(scope = backgroundScope) {
            initialState(VState.Idle)
            state<VState.Idle> {
                on<VAction.Go> {
                    reject()
                    transition(VState.Active) // must be no-op
                }
            }
        }
        store.state.test {
            awaitItem()
            store.dispatch(VAction.Go)
            delay(1)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(VState.Idle, store.state.value)
    }

    @Test
    fun reject_discardsAccumulatedEffects() = runTest {
        val effects = mutableListOf<VEffect>()
        val store = store<VState, VAction, VEffect>(
            scope = backgroundScope,
            plugins = listOf(plugin { onEffect { effects += effect as VEffect } })
        ) {
            initialState(VState.Idle)
            state<VState.Idle> {
                on<VAction.Go> {
                    sideEffect(VEffect.Ping) // accumulated before reject
                    reject()
                    sideEffect(VEffect.Pong) // must be no-op after reject
                }
            }
        }
        store.state.test {
            awaitItem()
            store.dispatch(VAction.Go)
            delay(1)
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(effects.isEmpty()) // all effects discarded by reject()
    }

    @Test
    fun transition_firstWriteWins_subsequentCallsIgnored() = runTest {
        val store = store<VState, VAction, VEffect>(scope = backgroundScope) {
            initialState(VState.Idle)
            state<VState.Idle> {
                on<VAction.Go> {
                    transition(VState.Active) // first call wins
                    transition(VState.Idle)   // must be ignored
                }
            }
        }
        store.state.test {
            awaitItem()
            store.dispatch(VAction.Go)
            assertEquals(VState.Active, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun guard_false_blocksSubsequentTransitionAndEffect() = runTest {
        val effects = mutableListOf<VEffect>()
        val store = store<VState, VAction, VEffect>(
            scope = backgroundScope,
            plugins = listOf(plugin { onEffect { effects += effect as VEffect } })
        ) {
            initialState(VState.Idle)
            state<VState.Idle> {
                on<VAction.Go> {
                    guard { false }
                    transition(VState.Active) // blocked
                    sideEffect(VEffect.Ping)  // blocked
                }
            }
        }
        store.state.test {
            awaitItem()
            store.dispatch(VAction.Go)
            delay(1)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(VState.Idle, store.state.value)
        assertTrue(effects.isEmpty())
    }

    @Test
    fun guard_false_preservesPreGuardEffects() = runTest {
        val effects = mutableListOf<VEffect>()
        val store = store<VState, VAction, VEffect>(
            scope = backgroundScope,
            plugins = listOf(plugin { onEffect { effects += effect as VEffect } })
        ) {
            initialState(VState.Idle)
            state<VState.Idle> {
                on<VAction.Go> {
                    sideEffect(VEffect.Ping) // recorded before guard
                    guard { false }
                    sideEffect(VEffect.Pong) // blocked
                    transition(VState.Active)  // blocked
                }
            }
        }
        store.state.test {
            awaitItem()
            store.dispatch(VAction.Go)
            delay(1)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(VState.Idle, store.state.value)
        assertEquals<List<VEffect>>(listOf(VEffect.Ping), effects) // pre-guard effect preserved
    }

    @Test
    fun guard_true_allowsSubsequentVerbs() = runTest {
        val effects = mutableListOf<VEffect>()
        val store = store<VState, VAction, VEffect>(
            scope = backgroundScope,
            plugins = listOf(plugin { onEffect { effects += effect as VEffect } })
        ) {
            initialState(VState.Idle)
            state<VState.Idle> {
                on<VAction.Go> {
                    guard { true }
                    transition(VState.Active)
                    sideEffect(VEffect.Ping)
                }
            }
        }
        store.state.test {
            awaitItem()
            store.dispatch(VAction.Go)
            assertEquals(VState.Active, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals<List<VEffect>>(listOf(VEffect.Ping), effects)
    }

    @Test
    fun reject_afterGuardFails_isNoOp_preGuardEffectsPreserved() = runTest {
        val effects = mutableListOf<VEffect>()
        val store = store<VState, VAction, VEffect>(
            scope = backgroundScope,
            plugins = listOf(plugin { onEffect { effects += effect as VEffect } })
        ) {
            initialState(VState.Idle)
            state<VState.Idle> {
                on<VAction.Go> {
                    sideEffect(VEffect.Ping) // recorded before guard
                    guard { false }          // guard fails → guarded = true
                    reject()                 // no-op: guarded takes precedence
                    transition(VState.Active)  // no-op: guarded
                }
            }
        }
        store.state.test {
            awaitItem()
            store.dispatch(VAction.Go)
            delay(1)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(VState.Idle, store.state.value)
        assertEquals<List<VEffect>>(listOf(VEffect.Ping), effects) // pre-guard effect preserved
    }

    @Test
    fun dispatch_fromHandler_enqueuesFollowUpAction() = runTest {
        val store = store<VState, VAction, VEffect>(scope = backgroundScope) {
            initialState(VState.Idle)
            state<VState.Idle> {
                on<VAction.Dispatch> {
                    dispatch(VAction.Dispatched)
                }
                on<VAction.Dispatched> {
                    transition(VState.Active)
                }
            }
        }
        store.state.test {
            awaitItem()
            store.dispatch(VAction.Dispatch)
            assertEquals(VState.Active, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun dispatchFromHandler_isBlockedByReject() = runTest {
        var dispatched = false
        val store = store<VState, VAction, VEffect>(scope = backgroundScope) {
            initialState(VState.Idle)
            state<VState.Idle> {
                on<VAction.Dispatch> {
                    reject()
                    dispatch(VAction.Dispatched) // no-op after reject
                }
                on<VAction.Dispatched> {
                    dispatched = true
                    transition(VState.Active)
                }
            }
        }
        store.state.test {
            awaitItem()
            store.dispatch(VAction.Dispatch)
            delay(1)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(VState.Idle, store.state.value)
        assertEquals(false, dispatched)
    }
}
