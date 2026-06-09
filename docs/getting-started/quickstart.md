# Quick start

This guide walks through the four steps needed to run a state machine end to end.

## 1. Define your types

Implement `State`, `Action`, and `Effect` for your feature. The simplest form uses a data class
for state and sealed interfaces for actions and effects:

```kotlin
data class CounterState(val count: Int) : State

sealed interface CounterAction : Action {
    data object Increment : CounterAction
    data object Decrement : CounterAction
    data object Reset     : CounterAction
}

sealed interface CounterEffect : Effect {
    data object Saved : CounterEffect
}
```

## 2. Build a store

Pass a `CoroutineScope` and configure states and handlers with the DSL:

```kotlin
val counter = store<CounterState, CounterAction, CounterEffect>(viewModelScope) {
    initialState(CounterState(0))

    state<CounterState> {
        on<CounterAction.Increment> { transition(state.copy(count = state.count + 1)) }
        on<CounterAction.Decrement> { transition(state.copy(count = state.count - 1)) }
        on<CounterAction.Reset> {
            transition(CounterState(0))
            sideEffect(CounterEffect.Saved)
        }
    }

    install(LoggingPlugin(tag = "Counter"))
}
```

`state<T>` registers a handler block for any state that is an instance of `T`. Multiple
`state` blocks can be registered — the most specific match (exact type or nearest supertype
via BFS) wins. See [Hierarchical states](../guide/hierarchical-states.md) for details.

## 3. Observe

Collect `state` and `effects` from any coroutine scope:

```kotlin
// State is a StateFlow — always holds the current value
counter.state.collect { render(it) }

// Effects are one-shot — use a dedicated collector so nothing is missed
counter.effects.collect { handle(it) }
```

In Compose, use the `toViewStore()` / `handleEffects { }` helpers from the sample module so
collection stops when the UI is backgrounded.

## 4. Dispatch actions

```kotlin
counter.dispatch(CounterAction.Increment)
counter.dispatch(CounterAction.Reset)
```

Actions are enqueued on an `UNLIMITED` channel and processed one at a time by a single
coroutine — dispatch is safe to call from any thread or coroutine.

---

## Android — ViewModel

```kotlin
class CounterViewModel : ViewModel() {
    val store = store<CounterState, CounterAction, CounterEffect>(viewModelScope) {
        initialState(CounterState(0))
        // …
    }
}
```

The store is cancelled automatically when `viewModelScope` is cleared.

## Compose Multiplatform — composition-scoped store

On non-Android targets (or when you want the store tied to composition lifetime rather than a
ViewModel), use `rememberStore`:

```kotlin
@Composable
fun CounterScreen() {
    val store = rememberStore { scope ->
        CounterStateMachine(scope, counterRepository)
    }
    // store is cancelled when the composable leaves the composition
}
```
