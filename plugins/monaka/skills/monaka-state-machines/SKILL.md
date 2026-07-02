---
name: monaka-state-machines
description: >-
  Author Monaka state machines: define State/Action/Effect, build stores with the store {} /
  stateMachine {} DSL, write on<> handlers and verbs (transition, sideEffect, reject, guard,
  dispatch, task, cancel), state & application lifecycle hooks (onEnter/onExit/onUpdate/onResume/
  onPause), hierarchical states, plugins, and error handling. Use whenever writing or editing
  Monaka MVI logic. For deps see monaka-setup; for tests see monaka-testing; for Compose see
  monaka-compose; for coordinating several machines see monaka-multi-machine.
---

# Authoring Monaka state machines

Monaka runs each machine as a single-coroutine actor: actions go through one
`Channel(UNLIMITED)` processed by one coroutine, so state transitions are sequential,
deterministic, and race-free regardless of how many threads call `dispatch()`. Full docs:
https://monaka.gmvalentino.dev

## 1. Define the types

State, Action, and Effect are marker interfaces from `dev.gmvalentino.monaka.core`. Model states
as a **sealed interface** so the compiler tracks every case; use `data class`/`data object`.

```kotlin
import dev.gmvalentino.monaka.core.*

sealed interface LoginState : State {
    data object Idle : LoginState
    data class Typing(val username: String, val password: String) : LoginState {
        val isValid: Boolean get() = username.isNotBlank() && password.isNotBlank()
    }
    data class Submitting(val username: String, val password: String) : LoginState
    data class Authenticated(val username: String) : LoginState
    data class Error(val message: String) : LoginState
}

sealed interface LoginAction : Action {
    data class UpdateCredentials(val username: String, val password: String) : LoginAction
    data object Submit : LoginAction
    data object Logout : LoginAction
}

sealed interface LoginEffect : Effect {
    data object NavigateToHome : LoginEffect
    data object NavigateToLogin : LoginEffect
    data class ShowValidationError(val message: String) : LoginEffect
}
```

- **State** = a snapshot. **Action** = intent (what happened / what the user wants).
  **Effect** = one-shot side effect (navigation, toast, analytics) — *not* state.
- Put computed helpers (like `isValid`) on the state, not in handlers.

## 2. Build the machine — three shapes

### a. Inline `store {}` (simplest; tied to a scope)

```kotlin
import dev.gmvalentino.monaka.dsl.store

val store = store<CounterState, CounterAction, CounterEffect>(viewModelScope) {
    initialState(CounterState(0))
    state<CounterState> {
        on<CounterAction.Increment> { transition(state.copy(count = state.count + 1)) }
    }
    install(LoggingPlugin(tag = "Counter"))
}
```

### b. Reusable `stateMachine {}` config (no scope; start later)

```kotlin
import dev.gmvalentino.monaka.dsl.stateMachine

val counterMachine = stateMachine<CounterState, CounterAction, CounterEffect> {
    initialState(CounterState(0))
    state<CounterState> { on<CounterAction.Increment> { transition(state.copy(count = state.count + 1)) } }
}
val store1 = store(counterMachine, scope1)
val store2 = store(counterMachine, scope2, initialState = CounterState(10))
```

### c. Named class with injected dependencies (recommended for real features)

Delegate the class to `stateMachine(builder = { … })`; handlers close over constructor params. The
class is an immutable config you can pass straight to `testStore` (see monaka-testing).

```kotlin
import dev.gmvalentino.monaka.dsl.StateMachine

class LoginStateMachine(
    private val loginRepository: LoginRepository,
) : StateMachine<LoginState, LoginAction, LoginEffect> by stateMachine(builder = {
    initialState(LoginState.Idle)
    // … states …
})
```

Start it with `store(machine, scope)`, typically from a ViewModel:

```kotlin
class LoginViewModel(repo: LoginRepository) : ViewModel() {
    val store = store(LoginStateMachine(repo), viewModelScope)
}
```

> To make a machine addressable by its own type (needed for `relay` / `StoreRegistry`), wrap it in
> a distinct `Store` class: `class LoginStore(m: LoginStateMachine, scope) : Store<…> by store(m, scope = scope)`.
> See monaka-multi-machine.

## 3. Handlers are statements, not expressions

Every `on<>` / hook lambda returns `Unit` and **records** what should happen by calling verbs on
its receiver. Doing nothing is a silent no-op. Inside a handler you have `state` (typed to the
`state<T>` block) and, in `on<>`, `action` (typed to the `on<A>` type).

| Verb | Behavior |
|---|---|
| `transition(newState)` | Record the new state. **First call wins** — later calls are no-ops (enables fallback patterns). |
| `sideEffect(e1, e2, …)` | Append effects (vararg, accumulates). Emitted after the state change, in call order. |
| `reject()` | Terminal. Marks the action rejected, **discards effects recorded before it**, and no-ops every later verb. Notifies plugins via `onRejected`. |
| `guard { predicate }` | Short-circuit later verbs if false — but **keeps** what was recorded before it and does *not* notify plugins. |
| `dispatch(action)` | Enqueue another action for later processing. |
| `task { … }` / `task("key") { … }` | Fire-and-forget coroutine (optionally keyed). |
| `cancel("key")` | Cancel the keyed job. |

```kotlin
state<LoginState.Typing> {
    on<LoginAction.UpdateCredentials> {
        transition(state.copy(username = action.username, password = action.password))
    }
    on<LoginAction.Submit> {
        if (!state.isValid) {
            sideEffect(LoginEffect.ShowValidationError("Please fill in all fields."))
        } else {
            transition(LoginState.Submitting(state.username, state.password))
        }
    }
}
```

`reject()` vs `guard {}`: use `reject()` to abort and drop everything (invalid action for this
state); use `guard {}` when effects recorded *before* the check should still fire (e.g. always emit
an analytics effect, but only transition when valid).

## 4. Calling suspend dependencies (repositories / use-cases)

Dependencies are injected by plain closure capture — no DI mechanism in the library. Three patterns:

### Pattern A — `onEnter` + `onError` (clean load-on-entry; recommended)

Transition to a "working" state; do the suspend call in its `onEnter`; branch to success/failure.
`onError` catches thrown exceptions for that state.

```kotlin
state<LoginState.Typing> {
    on<LoginAction.Submit> { transition(LoginState.Submitting(state.username, state.password)) }
}
state<LoginState.Submitting> {
    onEnter {
        val username = loginRepository.login(state.username, state.password) // suspend; may throw
        transition(LoginState.Authenticated(username))
        sideEffect(LoginEffect.NavigateToHome)
    }
    onError { transition(LoginState.Error(error.message ?: "Unknown error")) }
}
```

### Pattern B — inline suspend in `on<>` (blocks the queue; one at a time)

The handler suspends until the call returns; the action queue is paused meanwhile. Branch with
`runCatching { … }.fold(…)` rather than a hand-rolled `when`:

```kotlin
on<LoginAction.Submit> {
    runCatching { loginRepository.login(state.username, state.password) }.fold(
        onSuccess = { username ->
            transition(LoginState.Authenticated(username))
            sideEffect(LoginEffect.NavigateToHome)
        },
        onFailure = { transition(LoginState.Error(it.message ?: "Unknown error")) },
    )
}
```

### Pattern C — fire-and-dispatch with `task` (non-blocking; keyed for debounce/cancel)

The handler returns immediately; the coroutine dispatches a follow-up action when done. `task("key")`
cancels any running job with the same key first (debounce / latest-wins). Inside the block you have
`state`, `action`, and `dispatch` — but **not** the transition verbs.

```kotlin
on<SearchAction.QueryChanged> {
    task("search") {
        delay(300) // debounce
        dispatch(SearchAction.ResultsReceived(repository.search(action.query)))
    }
    transition(state.copy(query = action.query, isLoading = true))
}
on<SearchAction.Clear> {
    cancel("search")
    transition(SearchState.Idle)
}
```

> Uncaught exceptions inside a `task {}` are **not** routed to plugins/`onError`. Catch inside the
> block and `dispatch` an error action. Pass `autoCancel = true` to have the runtime cancel the job
> on the next state-type change (just before `onExit`).

## 5. State lifecycle hooks

Fire around transitions. Firing order on a state-type change: **`onExit`** (old) → **`onEnter`** (new).

```kotlin
state<FeedState.Live> {
    onEnter { task("poll") { while (true) { delay(5_000); dispatch(FeedAction.Refresh) } } }
    onExit  { cancel("poll") }
}
state<FeedState.Active> {
    onUpdate { if (fromState.query != state.query) task { analytics.track(state.query) } }
}
```

- `onEnter` fires when entering the type from a different type — **and once for the initial state**
  when the store starts (`start()` is called explicitly, or implicitly when `state`/`effects` are
  first collected; `monaka-test` calls it for you).
- `onUpdate` fires when the value changes but the type stays the same; it exposes `fromState`.

## 6. Application lifecycle hooks

Forward platform lifecycle events and react per state:

```kotlin
state<TimerState.Running> {
    onPause  { dispatch(TimerAction.Pause) }
    onResume { dispatch(TimerAction.Resume) }
}
```

Drive them with `store.onLifecycleEvent(LifecycleEvent.OnPause)`. Events: `OnCreate`, `OnStart`,
`OnResume`, `OnPause`, `OnStop`, `OnDestroy`. In Compose, `bindLifecycle()` wires these
automatically (see monaka-compose).

## 7. Hierarchical states

A `state<Parent>` block handles actions/hooks for **any** substate that doesn't have its own more
specific registration. Resolution: exact runtime class first, then registered ancestors in
**registration order** — so register broad parent blocks *after* leaf blocks.

```kotlin
state<LoginState.Submitting> {
    on<LoginAction.Cancel> { transition(LoginState.Idle) } // leaf wins for Submitting
}
state<LoginState> {
    on<LoginAction.Logout> {                                // catch-all for every LoginState
        transition(LoginState.Idle)
        sideEffect(LoginEffect.NavigateToLogin)
    }
}
```

## 8. Plugins

Synchronous observers called inside the processing coroutine — keep them fast; launch a coroutine
for heavy work. `install(...)` in the builder, or attach later with `store.install(...)`.

```kotlin
install(LoggingPlugin(tag = "Login"))
install(LoggingPlugin(tag = "Login") { tag, msg -> Log.d(tag, msg) }) // custom sink
```

Ad-hoc plugin via the `plugin {}` DSL, with optional per-type filtering:

```kotlin
install(plugin {
    onTransition { println("$fromState → $toState") }
    onAction<LoginAction.Submit> { analytics.trackLogin(action.username) } // action is typed
    onEffect<LoginEffect.NavigateToHome> { navigator.home() }
    onError<NetworkException> { crashReporter.record(error) }
})
```

Custom class implementing `Plugin` — all methods default to no-ops:

```kotlin
class AnalyticsPlugin : Plugin {
    override fun onTransition(fromState: State, toState: State) { analytics.track(toState) }
    override fun onError(error: Throwable, currentState: State, handlerType: HandlerType<Action>) {
        crashReporter.log(error)
    }
}
```

Plugin hooks: `onAction`, `onTransition`, `onEffect`, `onUnhandled` (no handler registered),
`onRejected` (`reject()` called), `onError`.

## 9. Error handling recap

- **Handler/hook throws** → caught, forwarded to plugins' `onError`, state unchanged. A matching
  state's `onError { }` hook can recover (e.g. `transition(Error(...))`).
- **`task {}` throws** → propagates to the coroutine scope, **not** to `onError`. Catch + dispatch.
- **No handler for an action** in the current state → `onUnhandled`, no state change.

## Reference

- `initialState(state)` is required (in the builder or via the `initialState =` argument).
- `name("…")` sets the store name used by plugins/logging.
- Async restore: pass `initializer = { … }` to `store(...)` to load persisted state before the
  first action (falls back to `initialState` on failure; `onEnter` still fires).
- Fuller guides: /guide/handlers, /guide/lifecycle-hooks, /guide/hierarchical-states,
  /guide/plugins, /guide/error-handling on https://monaka.gmvalentino.dev
