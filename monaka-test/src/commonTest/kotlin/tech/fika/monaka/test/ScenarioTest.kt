package tech.fika.monaka.test

import kotlin.test.Test
import tech.fika.monaka.core.Action
import tech.fika.monaka.core.Effect
import tech.fika.monaka.core.LifecycleEvent
import tech.fika.monaka.core.State
import tech.fika.monaka.dsl.stateMachine

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
            transition { CounterState.Counting(count = 0) }
        }
    }
    state<CounterState.Counting> {
        on<CounterAction.Increment> {
            transition { state.copy(count = state.count + 1) }
        }
        on<CounterAction.DoubleUp> {
            dispatch(CounterAction.Increment)
            dispatch(CounterAction.Increment)
        }
        onPause {
            sideEffect(CounterEffect.Persisted)
            transition { CounterState.Paused }
        }
    }
}

class ScenarioTest {

    @Test
    fun startAndIncrement() = testStore(machine = incrementingMachine) {
        scenario("start then increment") {
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
        scenario("double-up fans out two Increment actions") {
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
        scenario("pause persists and parks") {
            given(CounterState.Counting(count = 5))

            trigger(LifecycleEvent.OnPause) {
                expectState<CounterState.Paused>()
                expectEffect(CounterEffect.Persisted)
            }

        }
    }

    @Test
    fun multipleScenariosShareMachineButGetFreshStores() = testStore(machine = incrementingMachine) {
        scenario("first scenario starts from Idle") {
            trigger(CounterAction.Start) {
                expectState<CounterState.Counting> { state.count == 0 }
                expectEffect(CounterEffect.Started)
            }
        }

        scenario("second scenario also starts from Idle — proves isolation") {
            trigger(CounterAction.Start) {
                expectState<CounterState.Counting> { state.count == 0 }
                expectEffect(CounterEffect.Started)
            }
        }
    }
}
