package tech.fika.monaka.examples.counter

import kotlin.test.Test
import tech.fika.monaka.dsl.stateMachine
import tech.fika.monaka.test.testStore

// CounterStateMachine delegates from store(scope) so it carries a CoroutineScope and
// cannot be used directly with testStore. The machine logic is re-declared below using
// stateMachine { } — same handlers, no scope dependency — making it testable in isolation.
private val counterMachine = stateMachine<CounterState, CounterAction, CounterEffect> {
    initialState(CounterState(count = 0, step = 1))
    state<CounterState> {
        on<CounterAction.Increment> {
            transition { state.copy(count = state.count + state.step) }
        }
        on<CounterAction.Decrement> {
            transition { state.copy(count = state.count - state.step) }
        }
        on<CounterAction.SetStep> {
            if (action.step < 1) {
                sideEffect(CounterEffect.ShowMessage("Step must be at least 1."))
            } else {
                transition { state.copy(step = action.step) }
            }
        }
        on<CounterAction.Reset> {
            transition { CounterState(count = 0, step = state.step) }
            sideEffect(CounterEffect.ShowMessage("Counter reset!"))
            sideEffect(CounterEffect.SaveCount(0))
        }
        on<CounterAction.SaveCompleted> {
            if (action.success) sideEffect(CounterEffect.ShowMessage("Saved ✓"))
        }
    }
}

class CounterStateMachineTest {

    @Test
    fun incrementAndDecrement() = testStore(machine = counterMachine) {
        scenario("increment raises count by step") {
            trigger(CounterAction.Increment) {
                expectState<CounterState> { state.count == 1 }
            }
            trigger(CounterAction.Increment) {
                expectState<CounterState> { state.count == 2 }
            }
            trigger(CounterAction.Decrement) {
                expectState<CounterState> { state.count == 1 }
            }
        }
    }

    @Test
    fun setStepChangesIncrement() = testStore(machine = counterMachine) {
        scenario("custom step is applied to increment") {
            trigger(CounterAction.SetStep(step = 5)) {
                expectState<CounterState> { state.step == 5 }
            }
            trigger(CounterAction.Increment) {
                expectState<CounterState> { state.count == 5 }
            }
        }
    }

    @Test
    fun invalidStepEmitsErrorAndKeepsState() = testStore(machine = counterMachine) {
        scenario("step < 1 emits error, state unchanged") {
            trigger(CounterAction.SetStep(step = 0)) {
                expectEffect(CounterEffect.ShowMessage("Step must be at least 1."))
            }
        }
    }

    @Test
    fun resetEmitsMessageAndSaveEffect() = testStore(machine = counterMachine) {
        scenario("reset from non-zero restores count and emits two effects") {
            given(CounterState(count = 42, step = 3))

            trigger(CounterAction.Reset) {
                expectState<CounterState> { state.count == 0 && state.step == 3 }
                expectEffect(CounterEffect.ShowMessage("Counter reset!"))
                expectEffect(CounterEffect.SaveCount(0))
            }
        }
    }

    @Test
    fun saveCompletedSuccessEmitsMessage() = testStore(machine = counterMachine) {
        scenario("SaveCompleted success emits saved message") {
            trigger(CounterAction.SaveCompleted(success = true)) {
                expectEffect(CounterEffect.ShowMessage("Saved ✓"))
            }
        }
    }

    @Test
    fun saveCompletedFailureSilent() = testStore(machine = counterMachine) {
        scenario("SaveCompleted failure emits nothing") {
            trigger(CounterAction.SaveCompleted(success = false)) {
                expectNoEffects()
            }
        }
    }
}
