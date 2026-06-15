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

private sealed interface HKState : State {
    data object Idle : HKState
    data object Active : HKState
    data class Counting(val n: Int) : HKState
}

private sealed interface HKAction : Action {
    data object Activate : HKAction
    data object Deactivate : HKAction
    data object Increment : HKAction
}

private sealed interface HKEffect : Effect {
    data object Exited : HKEffect
}

class StateHookTest {

    @Test
    fun onEnter_firesForInitialState_whenStoreStarts() = runTest {
        var entered = false
        val store = store<HKState, HKAction, HKEffect>(scope = backgroundScope) {
            initialState(HKState.Idle)
            state<HKState.Idle> {
                onEnter { entered = true }
            }
        }
        store.state.test {
            awaitItem()   // initial state emitted; OnEnter is queued by start()
            delay(1)      // let processing job run and fire onEnter
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(entered)
    }

    @Test
    fun onEnter_firesOnStateTypeChange() = runTest {
        var entered = false
        val store = store<HKState, HKAction, HKEffect>(scope = backgroundScope) {
            initialState(HKState.Idle)
            state<HKState.Idle> {
                on<HKAction.Activate> { transition(HKState.Active) }
            }
            state<HKState.Active> {
                onEnter { entered = true }
            }
        }
        store.state.test {
            awaitItem()
            store.dispatch(HKAction.Activate)
            awaitItem() // HKState.Active — onEnter has fired
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(entered)
    }

    @Test
    fun onExit_firesOnStateTypeChange() = runTest {
        var exited = false
        val store = store<HKState, HKAction, HKEffect>(scope = backgroundScope) {
            initialState(HKState.Idle)
            state<HKState.Idle> {
                on<HKAction.Activate> { transition(HKState.Active) }
                onExit { exited = true }
            }
        }
        store.state.test {
            awaitItem()
            store.dispatch(HKAction.Activate)
            awaitItem() // HKState.Active — onExit has fired
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(exited)
    }

    @Test
    fun onExit_firesBeforeOnEnter() = runTest {
        val order = mutableListOf<String>()
        val store = store<HKState, HKAction, HKEffect>(scope = backgroundScope) {
            initialState(HKState.Idle)
            state<HKState.Idle> {
                on<HKAction.Activate> { transition(HKState.Active) }
                onExit { order += "exit-Idle" }
            }
            state<HKState.Active> {
                onEnter { order += "enter-Active" }
            }
        }
        store.state.test {
            awaitItem()
            store.dispatch(HKAction.Activate)
            awaitItem() // HKState.Active — both hooks have run
            cancelAndIgnoreRemainingEvents()
        }
        // Verify both fired and onExit came before onEnter (extra entries from initial
        // OnEnter are acceptable; we only check first occurrences)
        val exitIdx = order.indexOf("exit-Idle")
        val enterIdx = order.indexOf("enter-Active")
        assertTrue(exitIdx >= 0, "onExit should have fired")
        assertTrue(enterIdx >= 0, "onEnter should have fired")
        assertTrue(exitIdx < enterIdx, "onExit must fire before onEnter")
    }

    @Test
    fun onEnter_doesNotFire_onSameTypeValueChange() = runTest {
        var enterCount = 0
        val store = store<HKState, HKAction, HKEffect>(scope = backgroundScope) {
            initialState(HKState.Counting(0))
            state<HKState.Counting> {
                on<HKAction.Increment> { transition(state.copy(n = state.n + 1)) }
                onEnter { enterCount++ }
            }
        }
        store.state.test {
            awaitItem()  // Counting(0)
            delay(1)     // let initial onEnter run
            store.dispatch(HKAction.Increment)
            awaitItem()  // Counting(1) — same type, value changed; onEnter must NOT fire again
            delay(1)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, enterCount) // only the initial-state onEnter
    }

    @Test
    fun onUpdate_firesOnSameTypeValueChange_actionDriven() = runTest {
        var updateCount = 0
        val store = store<HKState, HKAction, HKEffect>(scope = backgroundScope) {
            initialState(HKState.Counting(0))
            state<HKState.Counting> {
                on<HKAction.Increment> { transition(state.copy(n = state.n + 1)) }
                onUpdate { updateCount++ }
            }
        }
        store.state.test {
            awaitItem()
            store.dispatch(HKAction.Increment)
            awaitItem() // Counting(1)
            delay(1)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, updateCount)
    }

    @Test
    fun onUpdate_doesNotFire_onStateTypeChange() = runTest {
        var updateFired = false
        val store = store<HKState, HKAction, HKEffect>(scope = backgroundScope) {
            initialState(HKState.Idle)
            state<HKState.Idle> {
                on<HKAction.Activate> { transition(HKState.Active) }
            }
            state<HKState.Active> {
                onUpdate { updateFired = true }
            }
        }
        store.state.test {
            awaitItem()
            store.dispatch(HKAction.Activate)
            awaitItem()
            delay(1)
            cancelAndIgnoreRemainingEvents()
        }
        assertFalse(updateFired)
    }

    @Test
    fun onEnter_canDispatchAction_causingFurtherTransition() = runTest {
        // StateFlow conflates fast successive transitions; use plugin to capture all.
        val transitions = mutableListOf<Pair<HKState, HKState>>()
        val store = store<HKState, HKAction, HKEffect>(
            scope = backgroundScope,
            plugins = listOf(
                plugin { onTransition { transitions += (fromState as HKState) to (toState as HKState) } }
            )
        ) {
            initialState(HKState.Idle)
            state<HKState.Idle> {
                on<HKAction.Activate> { transition(HKState.Active) }
            }
            state<HKState.Active> {
                onEnter { dispatch(HKAction.Deactivate) }
                on<HKAction.Deactivate> { transition(HKState.Idle) }
            }
        }
        store.state.test {
            awaitItem() // initial Idle
            store.dispatch(HKAction.Activate)
            delay(10)   // let both transitions process
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(2, transitions.size)
        assertEquals(HKState.Idle to HKState.Active, transitions[0])
        assertEquals(HKState.Active to HKState.Idle, transitions[1])
    }

    @Test
    fun onExit_canEmitEffect() = runTest {
        val effects = mutableListOf<HKEffect>()
        val store = store<HKState, HKAction, HKEffect>(
            scope = backgroundScope,
            plugins = listOf(plugin { onEffect { effects += effect as HKEffect } })
        ) {
            initialState(HKState.Active)
            state<HKState.Active> {
                on<HKAction.Deactivate> { transition(HKState.Idle) }
                onExit { sideEffect(HKEffect.Exited) }
            }
        }
        store.state.test {
            awaitItem()
            store.dispatch(HKAction.Deactivate)
            awaitItem() // Idle — onExit has fired
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals<List<HKEffect>>(listOf(HKEffect.Exited), effects)
    }
}
