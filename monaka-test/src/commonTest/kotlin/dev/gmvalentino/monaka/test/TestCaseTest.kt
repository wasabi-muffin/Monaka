package dev.gmvalentino.monaka.test

import kotlin.test.Test
import dev.gmvalentino.monaka.core.Action
import dev.gmvalentino.monaka.core.Effect
import dev.gmvalentino.monaka.core.LifecycleEvent
import dev.gmvalentino.monaka.core.State
import dev.gmvalentino.monaka.dsl.stateMachine

private sealed interface CounterState : State {
    data object Idle : CounterState
    data class Counting(val count: Int) : CounterState
    data object Paused : CounterState
}

private sealed interface CounterAction : Action {
    data object Start : CounterAction
    data object Increment : CounterAction
    data object DoubleUp : CounterAction
}

private sealed interface CounterEffect : Effect {
    data object Started : CounterEffect
    data object Persisted : CounterEffect
}

private val incrementingMachine = stateMachine<CounterState, CounterAction, CounterEffect> {
    initialState(CounterState.Idle)
    state<CounterState.Idle> {
        on<CounterAction.Start> {
            sideEffect(CounterEffect.Started)
            transition(CounterState.Counting(count = 0))
        }
    }
    state<CounterState.Counting> {
        on<CounterAction.Increment> {
            transition(state.copy(count = state.count + 1))
        }
        on<CounterAction.DoubleUp> {
            dispatch(CounterAction.Increment)
            dispatch(CounterAction.Increment)
        }
        onPause {
            sideEffect(CounterEffect.Persisted)
            transition(CounterState.Paused)
        }
    }
}

class TestCaseTest {

    @Test
    fun startAndIncrement() = testStore(machine = incrementingMachine) {
        testCase("start then increment") {
            trigger(CounterAction.Start) {
                expectState<CounterState.Counting> { state.count == 0 }
                expectEffect(CounterEffect.Started)
            }

            trigger(CounterAction.Increment) {
                expectState<CounterState.Counting> { state.count == 1 }
                expectNoEffects()
            }

        }
    }

    @Test
    fun handlerInitiatedDispatchSurfacesViaExpectAction() = testStore(machine = incrementingMachine) {
        testCase("double-up fans out two Increment actions") {
            given(CounterState.Counting(count = 0))

            trigger(CounterAction.DoubleUp) {
                expectAction(CounterAction.Increment)
                expectAction(CounterAction.Increment)
                expectState<CounterState.Counting> { state.count == 1 }
                expectState<CounterState.Counting> { state.count == 2 }
            }

        }
    }

    @Test
    fun lifecyclePauseTriggersStateChange() = testStore(machine = incrementingMachine) {
        testCase("pause persists and parks") {
            given(CounterState.Counting(count = 5))

            trigger(LifecycleEvent.OnPause) {
                expectState<CounterState.Paused>()
                expectEffect(CounterEffect.Persisted)
            }

        }
    }

    @Test
    fun multipleTestCasesShareMachineButGetFreshStores() = testStore(machine = incrementingMachine) {
        testCase("first test case starts from Idle") {
            trigger(CounterAction.Start) {
                expectState<CounterState.Counting> { state.count == 0 }
                expectEffect(CounterEffect.Started)
            }
        }

        testCase("second test case also starts from Idle — proves isolation") {
            trigger(CounterAction.Start) {
                expectState<CounterState.Counting> { state.count == 0 }
                expectEffect(CounterEffect.Started)
            }
        }
    }

    @Test
    fun finishSuppressesExpectIdle() = testStore(machine = incrementingMachine) {
        // DoubleUp dispatches two Increments. Asserting on only the first leaves a
        // pending state emission and a pending handler-action — exhaustive expectIdle()
        // would normally fail. finish() opts out.
        testCase("partial assertions are tolerated after finish()") {
            given(CounterState.Counting(count = 0))

            trigger(CounterAction.DoubleUp) {
                expectAction(CounterAction.Increment)
                expectState<CounterState.Counting> { state.count == 1 }
            }

            finish()
        }
    }
}
