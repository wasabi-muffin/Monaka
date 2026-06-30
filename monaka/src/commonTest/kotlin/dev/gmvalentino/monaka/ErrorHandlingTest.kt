package dev.gmvalentino.monaka

import app.cash.turbine.test
import dev.gmvalentino.monaka.core.Action
import dev.gmvalentino.monaka.core.Effect
import dev.gmvalentino.monaka.core.State
import dev.gmvalentino.monaka.dsl.store
import dev.gmvalentino.monaka.handler.HandlerType
import dev.gmvalentino.monaka.plugin.plugin
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private sealed interface EState : State {
    data object Idle : EState
    data object Active : EState
    data object Recovered : EState
}

private sealed interface EAction : Action {
    data object Trigger : EAction
}

private sealed interface EEffect : Effect {
    data object RecoveryEffect : EEffect
}

private class BoomException : Exception("boom")

class ErrorHandlingTest {

    @Test
    fun handlerThrows_stateUnchanged_andPluginNotified() = runTest {
        var caughtError: Throwable? = null
        var errorState: dev.gmvalentino.monaka.core.State? = null
        val store = store<EState, EAction, EEffect>(
            scope = backgroundScope,
            plugins = listOf(
                plugin {
                    onError {
                        caughtError = error
                        errorState = currentState
                    }
                },
            ),
        ) {
            initialState(EState.Idle)
            state<EState.Idle> {
                on<EAction.Trigger> { throw BoomException() }
            }
        }
        store.state.test {
            awaitItem()
            store.dispatch(EAction.Trigger)
            delay(1) // let handler throw and plugin fire
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(EState.Idle, store.state.value)
        assertNotNull(caughtError)
        assertTrue(caughtError is BoomException)
        assertEquals(EState.Idle, errorState)
    }

    @Test
    fun handlerThrows_withOnErrorHook_recoversWithTransition() = runTest {
        val store = store<EState, EAction, EEffect>(scope = backgroundScope) {
            initialState(EState.Idle)
            state<EState.Idle> {
                on<EAction.Trigger> { throw BoomException() }
                onError { transition(EState.Recovered) }
            }
        }
        store.state.test {
            awaitItem()
            store.dispatch(EAction.Trigger)
            assertEquals(EState.Recovered, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun handlerThrows_withOnErrorHook_recoversWithEffect() = runTest {
        val effects = mutableListOf<EEffect>()
        val store = store<EState, EAction, EEffect>(
            scope = backgroundScope,
            plugins = listOf(plugin { onEffect { effects += effect as EEffect } }),
        ) {
            initialState(EState.Idle)
            state<EState.Idle> {
                on<EAction.Trigger> { throw BoomException() }
                onError { sideEffect(EEffect.RecoveryEffect) }
            }
        }
        store.state.test {
            awaitItem()
            store.dispatch(EAction.Trigger)
            delay(1)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(EState.Idle, store.state.value)
        assertEquals<List<EEffect>>(listOf(EEffect.RecoveryEffect), effects)
    }

    @Test
    fun onErrorHook_itself_throws_pluginNotifiedAndStateUnchanged() = runTest {
        var pluginError: Throwable? = null
        val store = store<EState, EAction, EEffect>(
            scope = backgroundScope,
            plugins = listOf(plugin { onError { pluginError = error } }),
        ) {
            initialState(EState.Idle)
            state<EState.Idle> {
                on<EAction.Trigger> { throw BoomException() }
                onError { throw RuntimeException("recovery also failed") }
            }
        }
        store.state.test {
            awaitItem()
            store.dispatch(EAction.Trigger)
            delay(1)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(EState.Idle, store.state.value)
        assertNotNull(pluginError)
        assertTrue(pluginError is BoomException) // original error forwarded
    }

    @Test
    fun initializer_throws_fallsBackToInitialState_andNotifiesPlugin() = runTest {
        var restoreError: Throwable? = null
        var restoreHandlerType: HandlerType<*>? = null
        val store = store<EState, EAction, EEffect>(
            scope = backgroundScope,
            initializer = { throw BoomException() },
            plugins = listOf(
                plugin {
                    onError {
                        restoreError = error
                        restoreHandlerType = handlerType
                    }
                },
            ),
        ) {
            initialState(EState.Idle)
        }
        store.state.test {
            assertEquals(EState.Idle, awaitItem())
            delay(1) // let initializer error processing complete
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(EState.Idle, store.state.value)
        assertTrue(restoreError is BoomException)
        assertEquals(HandlerType.Restore, restoreHandlerType)
    }

    @Test
    fun initializer_succeeds_overridesInitialState() = runTest {
        val store = store<EState, EAction, EEffect>(
            scope = backgroundScope,
            initializer = { EState.Active },
        ) {
            initialState(EState.Idle)
        }
        store.state.test {
            awaitItem() // EState.Idle — stateIn initial value before initializer runs
            assertEquals(EState.Active, awaitItem()) // after initializer completes
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onErrorHook_receivesCorrectHandlerType_forAction() = runTest {
        var capturedHandlerType: HandlerType<*>? = null
        val store = store<EState, EAction, EEffect>(scope = backgroundScope) {
            initialState(EState.Idle)
            state<EState.Idle> {
                on<EAction.Trigger> { throw BoomException() }
                onError { capturedHandlerType = handlerType }
            }
        }
        store.state.test {
            awaitItem()
            store.dispatch(EAction.Trigger)
            delay(1)
            cancelAndIgnoreRemainingEvents()
        }
        val handlerType = capturedHandlerType
        assertTrue(handlerType is HandlerType.Action<*>)
        assertEquals(EAction.Trigger, handlerType.action)
    }
}
