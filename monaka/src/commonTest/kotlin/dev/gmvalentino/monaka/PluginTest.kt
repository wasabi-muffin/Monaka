package dev.gmvalentino.monaka

import app.cash.turbine.test
import app.cash.turbine.turbineScope
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

private sealed interface PState : State {
    data object Idle : PState
    data object Active : PState
}

private sealed interface PAction : Action {
    data object Go : PAction
    data object Unknown : PAction
    data object Reject : PAction
}

private sealed interface PEffect : Effect {
    data object Ping : PEffect
}

class PluginTest {

    @Test
    fun onAction_firesBeforeHandlerRuns() = runTest {
        val events = mutableListOf<String>()
        val store = store<PState, PAction, PEffect>(
            scope = backgroundScope,
            plugins = listOf(plugin { onAction { events += "plugin-action" } })
        ) {
            initialState(PState.Idle)
            state<PState.Idle> {
                on<PAction.Go> {
                    events += "handler"
                    transition(PState.Active)
                }
            }
        }
        store.state.test {
            awaitItem()
            store.dispatch(PAction.Go)
            awaitItem() // PState.Active — handler has run
            cancelAndIgnoreRemainingEvents()
        }
        // plugin.onAction fires before the handler
        assertEquals(listOf("plugin-action", "handler"), events)
    }

    @Test
    fun onTransition_firesOnSuccessfulTransition() = runTest {
        var fromState: dev.gmvalentino.monaka.core.State? = null
        var toState: dev.gmvalentino.monaka.core.State? = null
        val store = store<PState, PAction, PEffect>(
            scope = backgroundScope,
            plugins = listOf(plugin { onTransition { fromState = this.fromState; toState = this.toState } })
        ) {
            initialState(PState.Idle)
            state<PState.Idle> {
                on<PAction.Go> { transition(PState.Active) }
            }
        }
        store.state.test {
            awaitItem()
            store.dispatch(PAction.Go)
            awaitItem() // PState.Active — plugin callback has run
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(PState.Idle, fromState)
        assertEquals(PState.Active, toState)
    }

    @Test
    fun onTransition_doesNotFire_whenHandlerMakesNoTransition() = runTest {
        var transitionFired = false
        val store = store<PState, PAction, PEffect>(
            scope = backgroundScope,
            plugins = listOf(plugin { onTransition { transitionFired = true } })
        ) {
            initialState(PState.Idle)
            state<PState.Idle> {
                on<PAction.Go> { /* no transition */ }
            }
        }
        store.state.test {
            awaitItem()
            store.dispatch(PAction.Go)
            delay(1)
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(!transitionFired)
    }

    @Test
    fun onEffect_firesWhenEffectEmitted() = runTest {
        val capturedEffects = mutableListOf<PEffect>()
        val store = store<PState, PAction, PEffect>(
            scope = backgroundScope,
            plugins = listOf(plugin { onEffect { capturedEffects += effect as PEffect } })
        ) {
            initialState(PState.Idle)
            state<PState.Idle> {
                on<PAction.Go> { sideEffect(PEffect.Ping) }
            }
        }
        store.state.test {
            awaitItem()
            store.dispatch(PAction.Go)
            delay(1)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals<List<PEffect>>(listOf(PEffect.Ping), capturedEffects)
    }

    @Test
    fun onUnhandled_firesWhenNoHandlerRegistered() = runTest {
        var unhandledAction: PAction? = null
        val store = store<PState, PAction, PEffect>(
            scope = backgroundScope,
            plugins = listOf(plugin { onUnhandled { unhandledAction = action as PAction } })
        ) {
            initialState(PState.Idle)
            // no handler for Unknown
        }
        store.state.test {
            awaitItem()
            store.dispatch(PAction.Unknown)
            delay(1)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(PAction.Unknown, unhandledAction)
    }

    @Test
    fun onRejected_firesWhenHandlerCallsReject() = runTest {
        var rejectedHandlerType: HandlerType<*>? = null
        val store = store<PState, PAction, PEffect>(
            scope = backgroundScope,
            plugins = listOf(plugin { onRejected { rejectedHandlerType = handlerType } })
        ) {
            initialState(PState.Idle)
            state<PState.Idle> {
                on<PAction.Reject> { reject() }
            }
        }
        store.state.test {
            awaitItem()
            store.dispatch(PAction.Reject)
            delay(1)
            cancelAndIgnoreRemainingEvents()
        }
        val ht = rejectedHandlerType
        assertTrue(ht is HandlerType.Action<*>)
        assertEquals(PAction.Reject, ht.action)
    }

    @Test
    fun onError_firesWhenHandlerThrows() = runTest {
        var caughtError: Throwable? = null
        val store = store<PState, PAction, PEffect>(
            scope = backgroundScope,
            plugins = listOf(plugin { onError { caughtError = error } })
        ) {
            initialState(PState.Idle)
            state<PState.Idle> {
                on<PAction.Go> { throw RuntimeException("test error") }
            }
        }
        store.state.test {
            awaitItem()
            store.dispatch(PAction.Go)
            delay(1)
            cancelAndIgnoreRemainingEvents()
        }
        assertNotNull(caughtError)
        assertEquals("test error", caughtError?.message)
    }

    @Test
    fun multiplePlugins_fireInRegistrationOrder() = runTest {
        val order = mutableListOf<Int>()
        val plugins = listOf(
            plugin { onAction { order += 1 } },
            plugin { onAction { order += 2 } },
            plugin { onAction { order += 3 } },
        )
        val store = store<PState, PAction, PEffect>(scope = backgroundScope, plugins = plugins) {
            initialState(PState.Idle)
            state<PState.Idle> {
                on<PAction.Go> { /* no-op */ }
            }
        }
        store.state.test {
            awaitItem()
            store.dispatch(PAction.Go)
            delay(1)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf(1, 2, 3), order)
    }

    @Test
    fun install_afterConstruction_receivesSubsequentEvents() = runTest {
        val capturedTransitions = mutableListOf<Pair<PState, PState>>()
        val latePlugin = plugin {
            onTransition { capturedTransitions += (fromState as PState) to (toState as PState) }
        }
        val store = store<PState, PAction, PEffect>(scope = backgroundScope) {
            initialState(PState.Idle)
            state<PState.Idle> {
                on<PAction.Go> { transition(PState.Active) }
            }
        }
        store.state.test {
            awaitItem()
            store.install(latePlugin) // install after construction, before dispatch
            store.dispatch(PAction.Go)
            awaitItem() // PState.Active — latePlugin.onTransition has run
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals<List<Pair<PState, PState>>>(listOf(PState.Idle to PState.Active), capturedTransitions)
    }

    @Test
    fun typedOnAction_firesOnlyForMatchingType() = runTest {
        val captured = mutableListOf<PAction>()
        val store = store<PState, PAction, PEffect>(
            scope = backgroundScope,
            plugins = listOf(
                plugin { onAction<PAction.Go> { captured += action } }
            )
        ) {
            initialState(PState.Idle)
            state<PState.Idle> {
                on<PAction.Go> { /* no-op */ }
                on<PAction.Unknown> { /* no-op */ }
            }
        }
        store.state.test {
            awaitItem()
            store.dispatch(PAction.Unknown) // must NOT trigger typed plugin
            delay(1)
            store.dispatch(PAction.Go)      // must trigger typed plugin
            delay(1)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals<List<PAction>>(listOf(PAction.Go), captured)
    }
}
