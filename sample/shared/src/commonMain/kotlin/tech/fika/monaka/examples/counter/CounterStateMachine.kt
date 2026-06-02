package tech.fika.monaka.examples.counter

import kotlinx.coroutines.CoroutineScope
import tech.fika.monaka.core.Store
import tech.fika.monaka.dsl.store
import tech.fika.monaka.plugin.LoggingPlugin

class CounterStateMachine(
    scope: CoroutineScope,
) : Store<CounterState, CounterAction, CounterEffect> by store(
    scope = scope,
    builder = {
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
                    // Invalid step: stay, emit error message
                    sideEffect(CounterEffect.ShowMessage("Step must be at least 1."))
                } else {
                    transition { state.copy(step = action.step) }
                }
            }

            on<CounterAction.Reset> {
                transition {
                    CounterState(count = 0, step = state.step)
                }
                sideEffect(CounterEffect.ShowMessage("Counter reset!"))
                sideEffect(CounterEffect.SaveCount(0))
            }

            on<CounterAction.SaveCompleted> {
                if (action.success) sideEffect(CounterEffect.ShowMessage("Saved ✓"))
                // failed saves are silently ignored in this example
            }
        }

        install(LoggingPlugin(tag = "Counter"))
    }
)
