package dev.gmvalentino.monaka

import app.cash.turbine.test
import dev.gmvalentino.monaka.core.Action
import dev.gmvalentino.monaka.core.Effect
import dev.gmvalentino.monaka.core.State
import dev.gmvalentino.monaka.core.Store
import dev.gmvalentino.monaka.dsl.stateMachine
import dev.gmvalentino.monaka.dsl.store
import dev.gmvalentino.monaka.plugin.plugin
import dev.gmvalentino.monaka.relay.relay
import dev.gmvalentino.monaka.runtime.StoreRegistry
import dev.gmvalentino.monaka.runtime.register
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// ── Source store (one-shot: Idle → Active) ────────────────────────────────────

private sealed interface SrcState : State {
    data object Idle : SrcState
    data object Active : SrcState
}
private sealed interface SrcAction : Action {
    data object Go : SrcAction
}
private sealed interface SrcEffect : Effect

private val srcMachine = stateMachine<SrcState, SrcAction, SrcEffect> {
    initialState(SrcState.Idle)
    state<SrcState.Idle> {
        on<SrcAction.Go> { transition(SrcState.Active) }
    }
}

private class SourceStore(scope: CoroutineScope) :
    Store<SrcState, SrcAction, SrcEffect>
    by store(stateMachine = srcMachine, scope = scope)

// ── Cyclic source store (Off ↔ On) — used by relay lifecycle tests.
// Uses an inline stateMachine so each instance gets its own UUID. ──────────────

private sealed interface CycState : State {
    data object Off : CycState
    data object On : CycState
}
private sealed interface CycAction : Action {
    data object Toggle : CycAction
}
private sealed interface CycEffect : Effect

private class CyclicStore(scope: CoroutineScope) :
    Store<CycState, CycAction, CycEffect>
    by store(
        stateMachine = stateMachine {
            initialState(CycState.Off)
            state<CycState.Off> { on<CycAction.Toggle> { transition(CycState.On) } }
            state<CycState.On> { on<CycAction.Toggle> { transition(CycState.Off) } }
        },
        scope = scope,
    )

// ── Target store ──────────────────────────────────────────────────────────────

private sealed interface TgtState : State {
    data object Waiting : TgtState
    data object Notified : TgtState
}
private sealed interface TgtAction : Action {
    data object Notify : TgtAction
}
private sealed interface TgtEffect : Effect

private val tgtMachine = stateMachine<TgtState, TgtAction, TgtEffect> {
    initialState(TgtState.Waiting)
    state<TgtState.Waiting> {
        on<TgtAction.Notify> { transition(TgtState.Notified) }
    }
}

private class TargetStore(scope: CoroutineScope) :
    Store<TgtState, TgtAction, TgtEffect>
    by store(stateMachine = tgtMachine, scope = scope)

// ── Simple store for registration tests ──────────────────────────────────────

private sealed interface RState : State {
    data object Only : RState
}
private sealed interface RAction : Action
private sealed interface REffect : Effect

private class SimpleStore(scope: CoroutineScope) :
    Store<RState, RAction, REffect>
    by store(stateMachine = stateMachine { initialState(RState.Only) }, scope = scope)

class StoreRegistryTest {

    @Test
    fun register_storeIsRetrievableByClass() = runTest {
        val registry = StoreRegistry(backgroundScope)
        val store = SimpleStore(backgroundScope).register(registry)
        assertEquals(store, registry.get(SimpleStore::class))
    }

    @Test
    fun register_duplicateId_throwsIllegalArgument() = runTest {
        val registry = StoreRegistry(backgroundScope)
        val store = SimpleStore(backgroundScope).register(registry)
        assertFailsWith<IllegalArgumentException> {
            registry.register(store) // same instance → same id
        }
    }

    @Test
    fun unregister_removesStoreFromRegistry() = runTest {
        val registry = StoreRegistry(backgroundScope)
        val store = SimpleStore(backgroundScope).register(registry)
        assertTrue(SimpleStore::class in registry)
        registry.unregister(store)
        assertFalse(SimpleStore::class in registry)
    }

    @Test
    fun contains_trueIfRegistered_falseOtherwise() = runTest {
        val registry = StoreRegistry(backgroundScope)
        assertFalse(SimpleStore::class in registry)
        SimpleStore(backgroundScope).register(registry)
        assertTrue(SimpleStore::class in registry)
    }

    @Test
    fun getAll_returnsAllInstancesOfClass() = runTest {
        val registry = StoreRegistry(backgroundScope)
        val a = SimpleStore(backgroundScope).register(registry)
        val b = SimpleStore(backgroundScope).register(registry)
        val all = registry.getAll(SimpleStore::class)
        assertEquals(2, all.size)
        assertTrue(a in all)
        assertTrue(b in all)
    }

    @Test
    fun install_globalPlugin_appliedToExistingStore() = runTest {
        val capturedTransitions = mutableListOf<Pair<Any, Any>>()
        val registry = StoreRegistry(backgroundScope)
        val store = SourceStore(backgroundScope).register(registry)
        registry.install {
            plugin { onTransition { capturedTransitions += fromState to toState } }
        }
        store.state.test {
            awaitItem()
            store.dispatch(SrcAction.Go)
            awaitItem() // transition happened — plugin has fired
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(capturedTransitions.any { it == (SrcState.Idle to SrcState.Active) })
    }

    @Test
    fun install_globalPlugin_appliedToFutureStore() = runTest {
        val capturedTransitions = mutableListOf<Pair<Any, Any>>()
        val registry = StoreRegistry(backgroundScope)
        registry.install {
            plugin { onTransition { capturedTransitions += fromState to toState } }
        }
        val store = SourceStore(backgroundScope).register(registry)
        store.state.test {
            awaitItem()
            store.dispatch(SrcAction.Go)
            awaitItem() // transition happened — plugin has fired
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(capturedTransitions.any { it == (SrcState.Idle to SrcState.Active) })
    }

    @Test
    fun relay_dispatchesToTargetStore_onSourceStateChange() = runTest {
        val registry = StoreRegistry(backgroundScope)
        registry.bind(
            relay(from = SourceStore::class) {
                state<SrcState.Active> {
                    dispatch(TargetStore::class, TgtAction.Notify)
                }
            },
        )
        val source = SourceStore(backgroundScope).register(registry)
        val target = TargetStore(backgroundScope).register(registry)

        target.state.test {
            assertEquals(TgtState.Waiting, awaitItem())
            source.dispatch(SrcAction.Go)
            assertEquals(TgtState.Notified, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun relay_boundAfterRegistration_startsSampling() = runTest {
        val registry = StoreRegistry(backgroundScope)
        val source = SourceStore(backgroundScope).register(registry)
        val target = TargetStore(backgroundScope).register(registry)
        // bind AFTER both stores are registered
        registry.bind(
            relay(from = SourceStore::class) {
                state<SrcState.Active> {
                    dispatch(TargetStore::class, TgtAction.Notify)
                }
            },
        )
        target.state.test {
            assertEquals(TgtState.Waiting, awaitItem())
            source.dispatch(SrcAction.Go)
            assertEquals(TgtState.Notified, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Relay target-lifecycle tests ──────────────────────────────────────────

    /**
     * After the first dispatch the relay auto-tracks TargetStore as a declared target.
     * This validates the mechanism driving the handler-skipping tests below.
     */
    @Test
    fun relay_targets_autoTrackedOnFirstDispatch() = runTest {
        val registry = StoreRegistry(backgroundScope)
        val testRelay = relay(from = CyclicStore::class) {
            state<CycState.On> { dispatch(TargetStore::class, TgtAction.Notify) }
        }
        registry.bind(testRelay)
        assertTrue(testRelay.targets.isEmpty())

        val source = CyclicStore(backgroundScope).register(registry)
        val target = TargetStore(backgroundScope).register(registry)

        target.state.test {
            assertEquals(TgtState.Waiting, awaitItem())
            source.dispatch(CycAction.Toggle) // Off → On: relay fires, target tracked
            assertEquals(TgtState.Notified, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(TargetStore::class in testRelay.targets)
    }

    /**
     * When the target is unregistered the relay handler is skipped on subsequent source
     * emissions. The collector coroutine keeps running.
     */
    @Test
    fun relay_targetUnregistered_handlerSkippedOnSubsequentEmissions() = runTest {
        var handlerCallCount = 0
        val registry = StoreRegistry(backgroundScope)
        registry.bind(
            relay(from = CyclicStore::class) {
                state<CycState.On> {
                    handlerCallCount++
                    dispatch(TargetStore::class, TgtAction.Notify)
                }
            },
        )
        val source = CyclicStore(backgroundScope).register(registry)
        val target = TargetStore(backgroundScope).register(registry)

        // First toggle: Off → On. Relay fires, TargetStore discovered.
        target.state.test {
            assertEquals(TgtState.Waiting, awaitItem())
            source.dispatch(CycAction.Toggle)
            assertEquals(TgtState.Notified, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, handlerCallCount)

        // Toggle back: On → Off.
        source.state.test {
            awaitItem() // On (current)
            source.dispatch(CycAction.Toggle)
            assertEquals(CycState.Off, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        // Unregister the only target.
        registry.unregister(target)
        assertFalse(TargetStore::class in registry)

        // Toggle On again: handler must NOT fire because target is absent.
        source.state.test {
            assertEquals(CycState.Off, awaitItem()) // current state
            source.dispatch(CycAction.Toggle)
            assertEquals(CycState.On, awaitItem()) // state transitions normally
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(1, handlerCallCount) // unchanged
    }

    /**
     * When a target is re-registered after being absent, the handler fires again on the
     * next source emission.
     */
    @Test
    fun relay_targetReregistered_handlerFiresAgain() = runTest {
        val registry = StoreRegistry(backgroundScope)
        registry.bind(
            relay(from = CyclicStore::class) {
                state<CycState.On> { dispatch(TargetStore::class, TgtAction.Notify) }
            },
        )
        val source = CyclicStore(backgroundScope).register(registry)
        val target1 = TargetStore(backgroundScope).register(registry)

        // Fire relay with target1: Off → On.
        target1.state.test {
            assertEquals(TgtState.Waiting, awaitItem())
            source.dispatch(CycAction.Toggle)
            assertEquals(TgtState.Notified, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        // Toggle back to Off, unregister target1.
        source.state.test {
            awaitItem() // On (current)
            source.dispatch(CycAction.Toggle)
            assertEquals(CycState.Off, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        registry.unregister(target1)

        // Register a fresh target2. Toggle source On — handler should fire again.
        val target2 = TargetStore(backgroundScope).register(registry)
        target2.state.test {
            assertEquals(TgtState.Waiting, awaitItem())
            source.dispatch(CycAction.Toggle) // Off → On
            assertEquals(TgtState.Notified, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
