package dev.gmvalentino.monaka

import app.cash.turbine.test
import dev.gmvalentino.monaka.core.Action
import dev.gmvalentino.monaka.core.Effect
import dev.gmvalentino.monaka.core.State
import dev.gmvalentino.monaka.dsl.store
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

// Sealed hierarchy: HState → HState.Loading, HState.Active, HState.Error
private sealed interface HState : State {
    data object Idle : HState
    data class Loading(val id: Int) : HState
    data class Active(val data: String) : HState
    data object Error : HState
}

private sealed interface HAction : Action {
    data object Cancel : HAction        // handled by parent HState block
    data object Retry : HAction         // handled only by HState.Error
    data class Load(val id: Int) : HAction
}

private sealed interface HEffect : Effect {
    data object Canceled : HEffect
}

class HierarchicalStateTest {

    @Test
    fun parentBlock_catchesActionFromChildState() = runTest {
        // Cancel is registered on parent HState — should fire from any substate
        val store = store<HState, HAction, HEffect>(scope = backgroundScope) {
            initialState(HState.Loading(id = 1))
            state<HState> {
                on<HAction.Cancel> { transition(HState.Idle) }
            }
        }
        store.state.test {
            assertEquals(HState.Loading(id = 1), awaitItem())
            store.dispatch(HAction.Cancel)
            assertEquals(HState.Idle, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun leafHandler_takesPriority_overParentHandler() = runTest {
        // Retry is registered on both parent HState and HState.Error.
        // When in HState.Error, the leaf handler should win.
        val store = store<HState, HAction, HEffect>(scope = backgroundScope) {
            initialState(HState.Error)
            state<HState.Error> {
                on<HAction.Retry> { transition(HState.Loading(id = 0)) } // leaf wins
            }
            state<HState> {
                on<HAction.Retry> { transition(HState.Idle) } // parent — should not fire
            }
        }
        store.state.test {
            assertEquals(HState.Error, awaitItem())
            store.dispatch(HAction.Retry)
            assertEquals(HState.Loading(id = 0), awaitItem()) // leaf result
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun parentBlock_catchesFromMultipleChildStates() = runTest {
        // Cancel registered on parent only — should work from Loading AND Active
        val store = store<HState, HAction, HEffect>(scope = backgroundScope) {
            initialState(HState.Idle)
            state<HState.Idle> {
                on<HAction.Load> { transition(HState.Loading(action.id)) }
            }
            state<HState.Loading> {
                on<HAction.Load> { transition(HState.Active("loaded")) }
            }
            state<HState> {
                on<HAction.Cancel> {
                    sideEffect(HEffect.Canceled)
                    transition(HState.Idle)
                }
            }
        }
        store.state.test {
            awaitItem() // Idle
            store.dispatch(HAction.Load(42))
            awaitItem() // Loading

            // Cancel from Loading state — parent catches it
            store.dispatch(HAction.Cancel)
            assertEquals(HState.Idle, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun unregisteredAction_inChildState_fallsThroughToParent() = runTest {
        // Retry has no handler in Loading but HState has a fallback
        val store = store<HState, HAction, HEffect>(scope = backgroundScope) {
            initialState(HState.Loading(id = 1))
            state<HState> {
                on<HAction.Retry> { transition(HState.Idle) }
            }
            // No state<HState.Loading> block registered
        }
        store.state.test {
            awaitItem() // Loading
            store.dispatch(HAction.Retry)
            assertEquals(HState.Idle, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
