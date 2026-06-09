# Counter

**Source:** `sample/shared/…/examples/counter/`

The counter is the simplest example — a single-state machine with synchronous transitions and a
mix of inline effects and fire-and-dispatch async patterns. It demonstrates that a machine does
not need a sealed state hierarchy: a single `data class` as the state type is perfectly valid.

---

## Types

```kotlin
data class CounterState(val count: Int, val step: Int = 1) : State

sealed interface CounterAction : Action {
    data object Increment : CounterAction
    data object Decrement : CounterAction
    data class  SetStep(val step: Int) : CounterAction
    data object Reset : CounterAction
    // Dispatched back by the consumer after completing an async save:
    data class  SaveCompleted(val success: Boolean) : CounterAction
}

sealed interface CounterEffect : Effect {
    data class ShowMessage(val text: String) : CounterEffect
    // Instructs the consumer to persist the count; result comes back as SaveCompleted:
    data class SaveCount(val count: Int)     : CounterEffect
}
```

---

## State machine

```kotlin
class CounterStateMachine(scope: CoroutineScope) :
    Store<CounterState, CounterAction, CounterEffect> by store(scope = scope, builder = {

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

    install(LoggingPlugin(tag = "Counter"))
})
```

---

## Patterns demonstrated

### Single-state machine

The entire state fits in one `data class`. There is only one `state<CounterState>` block — no
hierarchy, no parent catch-all. All four action handlers live inside it.

### Conditional side effect without a transition

`SetStep` validates the input and either emits an effect (invalid) or transitions (valid) — no
`else` needed because `sideEffect` and `transition` are independent verb calls:

```kotlin
on<CounterAction.SetStep> {
    if (action.step < 1) {
        sideEffect(CounterEffect.ShowMessage("Step must be at least 1."))
    } else {
        transition(state.copy(step = action.step))
    }
}
```

### Multiple effects from one handler

`Reset` records a transition **and** two effects. Effects are emitted in call order after the
state change:

```kotlin
on<CounterAction.Reset> {
    transition(state.copy(count = 0))
    sideEffect(CounterEffect.ShowMessage("Counter reset!"))
    sideEffect(CounterEffect.SaveCount(0))   // tells the UI/ViewModel to persist
}
```

### Round-trip async save via actions

`SaveCount` is an effect that the consumer (ViewModel or Composable) handles by initiating an
async save. When the save completes, the consumer dispatches `SaveCompleted` back to the store,
which reacts with another effect:

```kotlin
// In the ViewModel or Composable:
store.handleEffects { effect ->
    when (effect) {
        is CounterEffect.SaveCount -> {
            val ok = repository.save(effect.count)
            store.dispatch(CounterAction.SaveCompleted(success = ok))
        }
        is CounterEffect.ShowMessage -> showSnackbar(effect.text)
    }
}
```

This keeps async persistence logic **outside** the machine, which remains a pure function of
actions → state/effects.
