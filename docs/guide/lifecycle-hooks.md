# Lifecycle hooks

Monaka has two kinds of lifecycle hook: **state lifecycle hooks** that fire when the machine
enters or leaves a state, and **app lifecycle hooks** that forward platform events (pause, resume,
etc.) into the machine.

---

## State lifecycle hooks

State lifecycle hooks are declared inside a `state<T> { }` block and fire **after** a successful
transition.

| Hook | When it fires |
|---|---|
| `onEnter { }` | The machine just entered this state type (from a different type). Does **not** fire for the initial state. |
| `onExit { }` | The machine is about to leave this state type. |
| `onUpdate { }` | The state type stayed the same but the value changed (e.g. a data class field was updated). |

Firing order on a type change: **`onExit`** (previous state) → **`onEnter`** (next state).

```kotlin
state<MyState.Loading> {
    onEnter {
        // Start a polling loop when Loading is entered
        task("poll") {
            while (true) {
                delay(5_000)
                dispatch(MyAction.Refresh)
            }
        }
    }
    onExit {
        // Cancel the loop when leaving Loading
        cancel("poll")
    }
}

state<MyState.Active> {
    onUpdate {
        // fromState and toState are available in the scope
        if (fromState.query != toState.query) {
            task { analytics.track(toState.query) }
        }
    }
}
```

All [handler verbs](handlers.md#handler-verbs) (`transition`, `sideEffect`, `task`, `dispatch`,
`cancel`) are available inside hook lambdas with the same semantics as in `on<>` handlers.

---

## App lifecycle hooks

Forward Android / iOS lifecycle events into the machine by calling `onLifecycleEvent` on the
store:

```kotlin
// From a ViewModel, Composable, or lifecycle observer:
store.onLifecycleEvent(LifecycleEvent.OnResume)
store.onLifecycleEvent(LifecycleEvent.OnPause)
```

React to those events in the DSL using the matching hook methods:

```kotlin
state<TimerState.Running> {
    onPause  { dispatch(TimerAction.Pause)  }
    onResume { dispatch(TimerAction.Resume) }
}
```

Available events and their hook methods:

| `LifecycleEvent` | Hook method |
|---|---|
| `OnCreate` | `onCreate { }` |
| `OnStart` | `onStart { }` |
| `OnResume` | `onResume { }` |
| `OnPause` | `onPause { }` |
| `OnStop` | `onStop { }` |
| `OnDestroy` | `onDestroy { }` |

### Compose Multiplatform

The `BindLifecycle()` composable (from the sample module) bridges
`androidx.lifecycle.Lifecycle.Event` to `LifecycleEvent` and forwards events automatically,
with no `expect`/`actual` required:

```kotlin
@Composable
fun MyScreen(store: Store<MyState, MyAction, MyEffect>) {
    store.bindLifecycle()  // wires up all lifecycle events automatically
    // …
}
```
