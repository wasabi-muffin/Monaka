package dev.gmvalentino.monaka.examples.counter

import kotlinx.coroutines.CoroutineScope
import dev.gmvalentino.monaka.core.Store
import dev.gmvalentino.monaka.dsl.store
import dev.gmvalentino.monaka.plugin.LoggingPlugin

class CounterStateMachine(
    scope: CoroutineScope,
) : Store<CounterState, CounterAction, CounterEffect> by store(
    scope = scope,
    builder = {
        initialState(CounterState(count = 0, step = 1))

        state<CounterState> {
            on<CounterAction.Increment> {
                transition(state.copy(count = state.count + state.step))
            }

            on<CounterAction.Decrement> {
                transition(state.copy(count = state.count - state.step))
            }

            on<CounterAction.SetStep> {
                if (action.step < 1) {
                    sideEffect(CounterEffect.ShowMessage("Step must be at least 1."))
                } else {
                    transition(state.copy(step = action.step))
                }
            }

            on<CounterAction.Reset> {
                transition(state.copy(count = 0))
                sideEffect(CounterEffect.ShowMessage("Counter reset!"))
                sideEffect(CounterEffect.SaveCount(0))
            }

            on<CounterAction.SaveCompleted> {
                if (action.success) sideEffect(CounterEffect.ShowMessage("Saved ✓"))
            }
        }
    }
)
