# Monaka

A Kotlin Multiplatform MVI state machine library. It provides a type-safe DSL for defining states, actions, effects, and their transitions, backed by a single-coroutine actor model that guarantees deterministic, race-free state updates.

**Targets:** Android · iOS (arm64, x64, Simulator arm64) · JVM

---

## Core concepts

| Concept | Interface | Role |
|---|---|---|
| **State** | `State` | Snapshot of the machine at a point in time |
| **Action** | `Action` | Intent dispatched by the UI or system |
| **Effect** | `Effect` | One-shot side effect (navigation, toast, analytics) |
| **Store** | `Store<S,A,E>` | Running instance; exposes `state`, `effects`, `dispatch` |

Effects are one-shot: they are emitted on a `SharedFlow` with no replay, so late subscribers miss past emissions.

---

## Setup

```kotlin
// build.gradle.kts (commonMain or Android)
implementation("tech.fika:monaka:<version>")
```

---

## Sample app

The sample is grouped under `sample/`:

```
sample/
├── shared/       Compose Multiplatform UI + state machines (Android · iOS)
├── androidApp/   Android entry (thin MainActivity)
└── iosApp/       Xcode project (SwiftUI shell)
```

**Android:**
```bash
./gradlew :sample:androidApp:assembleDebug
# install the APK from sample/androidApp/build/outputs/apk/debug/
```

**iOS:**
1. Open `sample/iosApp/iosApp.xcodeproj` in Xcode.
2. Edit `sample/iosApp/Configuration/Config.xcconfig` and set `TEAM_ID=` to your Apple developer team ID (only needed for physical-device runs).
3. Pick a simulator and run. Xcode will invoke `./gradlew :sample:shared:embedAndSignAppleFrameworkForXcode` as part of the build and link `shared.framework` automatically.

The same `App()` composable in `sample/shared/src/commonMain/kotlin/tech/fika/monaka/sample/App.kt` powers both platforms. Platform-specific lifecycle observation goes through an `expect`/`actual` `BindLifecycle()` composable.

---

## Quick start

```kotlin
// 1. Define your types
data class CounterState(val count: Int) : State

sealed interface CounterAction : Action {
    data object Increment : CounterAction
    data object Decrement : CounterAction
    data object Reset     : CounterAction
}

sealed interface CounterEffect : Effect {
    data object Saved : CounterEffect
}

// 2. Build a store
val counter = store<CounterState, CounterAction, CounterEffect>(viewModelScope) {
    initialState(CounterState(0))

    state<CounterState> {
        on<CounterAction.Increment> { transition { state.copy(count = state.count + 1) } }
        on<CounterAction.Decrement> { transition { state.copy(count = state.count - 1) } }
        on<CounterAction.Reset>     { transition(CounterEffect.Saved) { CounterState(0) } }
    }

    install(LoggingPlugin(tag = "Counter"))
}

// 3. Observe
counter.state.collect   { render(it) }
counter.effects.collect { handle(it) }

// 4. Dispatch
counter.dispatch(CounterAction.Increment)
```

---

## Handler patterns

Every `on<>` lambda has `ActionScope` as its implicit receiver, which gives access to `state`, `action`, and several helpers.

### Inline suspend (blocking)

The handler suspends until the coroutine returns. The action queue is paused for that duration — one request at a time.

```kotlin
on<LoginAction.Submit> {
    val result = loginRepository.login(state.username, state.password)
    when (result) {
        is Success -> transition { LoginState.Authenticated(result.user) }
        is Failure -> transition { LoginState.Error(result.message) }
    }
}
```

### Fire-and-dispatch (non-blocking)

Return immediately and let the async work dispatch a follow-up action when done.

```kotlin
on<LoginAction.Submit> {
    launch("login") {
        val r = loginRepository.login(state.username, state.password)
        dispatch(if (r is Success) LoginAction.LoginSucceeded(r.user) else LoginAction.LoginFailed(r.message))
    }
    transition { LoginState.Submitting }
}
```

### Keyed jobs (debounce / cancel)

`launch(key)` cancels any running job with the same key before starting a new one.

```kotlin
on<SearchAction.QueryChanged> {
    launch("search") {
        delay(300)
        dispatch(SearchAction.ResultsReceived(repository.search(action.query)))
    }
    transition { state.copy(query = action.query, isLoading = true) }
}

on<SearchAction.Clear> {
    cancel("search")
    transition { SearchState.Idle }
}
```

---

## Handler verbs

Handlers are **statements**, not expressions — call these methods on the scope to record what
the runtime should do. The lambda itself returns `Unit`; doing nothing is a silent no-op.

| Verb | Behaviour |
|---|---|
| `transition { newState }` | Record the new state. **First call wins** — later calls are ignored and their blocks are not evaluated. |
| `transition(effect1, effect2) { newState }` | Sugar for `transition { newState }; sideEffect(effect1, effect2)`. |
| `sideEffect(effect1, effect2)` | Append effects to be emitted in call order, after the state change (if any). |
| `reject()` | Mark the action as rejected. **Terminal** — subsequent `transition`, `sideEffect`, `dispatch`, `launch`, `cancel` calls become no-ops. Plugins notified via `onInvalid`. |
| `dispatch(action)` | Enqueue an action for later processing. |
| `launch { }` / `launch("key") { }` | Fire-and-forget coroutine (optionally keyed for debounce). |
| `cancel("key")` | Cancel the keyed coroutine. |

### First-write-wins for `transition`

Use it for fallback patterns:

```kotlin
on<Refresh> {
    if (state.isStale) transition { Refreshing }
    transition { Active }   // fallback when not stale
}
```

If you genuinely want exclusive selection, use `if/else` so the second branch is unreachable.

### Terminal `reject`

```kotlin
on<Submit> {
    if (!state.isValid) { reject(); return@on }
    transition { Submitting }
    sideEffect(Analytics.Started)
}
```

Once `reject()` is called, the runtime treats the action as rejected regardless of what was
recorded before it.

---

## State lifecycle hooks

Hooks fire **after** a successful transition.

```kotlin
state<MyState.Loading> {
    onEnter {
        // Machine just entered Loading — start a polling loop
        launch("poll") {
            while (true) {
                delay(5_000)
                dispatch(MyAction.Refresh)
            }
        }
    }
    onExit {
        // Machine is leaving Loading — stop the loop
        cancel("poll")
    }
}

state<MyState.Active> {
    onUpdate {
        // State value changed but type stayed Active
        if (fromState.query != toState.query) launch { analytics.track(toState.query) }
    }
}
```

Hook firing order on a type change: **`onExit`** (old state) → **`onEnter`** (new state).

`onEnter` does **not** fire for the initial state.

---

## Application lifecycle hooks

Forward Android/iOS lifecycle events into the machine:

```kotlin
// Forward from ViewModel or Composable:
store.onLifecycleEvent(LifecycleEvent.OnResume)
store.onLifecycleEvent(LifecycleEvent.OnPause)

// React in the DSL:
state<TimerState.Running> {
    onPause  { dispatch(TimerAction.Pause)  }
    onResume { dispatch(TimerAction.Resume) }
}
```

Available events: `OnCreate`, `OnStart`, `OnResume`, `OnPause`, `OnStop`, `OnDestroy`.

---

## Hierarchical state handling

Register a `state<ParentState>` block to handle actions from **any substate** without listing every leaf. Leaf registrations take priority.

```kotlin
// Logout works from any LoginState subtype:
state<LoginState> {
    on<LoginAction.Logout> {
        transition(LoginEffect.NavigateToLogin) { LoginState.Idle }
    }
}

// But Loading has its own Cancel handler that takes precedence:
state<LoginState.Loading> {
    on<LoginAction.Cancel> { transition { LoginState.Idle } }
}
```

---

## Plugins

Plugins observe machine events synchronously inside the processing coroutine. Keep them fast; launch coroutines for heavy work.

```kotlin
install(LoggingPlugin(tag = "Auth"))
```

### Custom plugin

```kotlin
class AnalyticsPlugin : Plugin<MyState, MyAction, MyEffect> {
    override fun onTransition(fromState: MyState, toState: MyState, action: MyAction) {
        analytics.track("transition", mapOf("from" to fromState, "to" to toState))
    }
    override fun onError(error: Throwable, currentState: MyState, handlerType: HandlerType<MyAction>) {
        crashReporter.log(error)
    }
}
```

---

## Named-class machines (`StateMachineStore`)

For larger machines, extend `StateMachineStore` to define the configuration as a class rather than a lambda:

```kotlin
class LoginStateMachine(
    scope: CoroutineScope,
    private val repo: LoginRepository,
) : StateMachineStore<LoginState, LoginAction, LoginEffect>(
    scope = scope,
    builder = {
        initialState(LoginState.Idle)

        state<LoginState.Idle> {
            on<LoginAction.TypeCredentials> {
                transition { LoginState.Typing(action.username, action.password) }
            }
        }

        state<LoginState.Typing> {
            on<LoginAction.Submit> { transition { LoginState.Submitting(state.username, state.password) } }
        }

        state<LoginState.Submitting> {
            onEnter {
                val result = repo.login(state.username, state.password)
                when (result) {
                    is Success -> transition(LoginEffect.NavigateToHome) { LoginState.Authenticated(result.user) }
                    is Failure -> transition { LoginState.Error(result.reason) }
                }
            }
        }

        state<LoginState> {
            on<LoginAction.Logout> { transition(LoginEffect.NavigateToLogin) { LoginState.Idle } }
        }

        install(LoggingPlugin(tag = "Login"))
    },
)
```

---

## Reusable configurations (`stateMachine`)

Separate configuration from execution to share or inspect a definition before running it:

```kotlin
val loginMachineConfig = stateMachine<LoginState, LoginAction, LoginEffect> {
    initialState(LoginState.Idle)
    // …
}

// Start multiple independent instances:
val store1 = store(loginMachineConfig, scope1)
val store2 = store(loginMachineConfig, scope2, initialState = LoginState.Typing("alice"))
```

---

## Multi-machine coordination

Use `StoreRegistry` and `Binder` to wire machines together declaratively.

```kotlin
val registry = StoreRegistry(viewModelScope)

// Auth state → Cart actions
registry.install(
    binder(from = AuthStateMachine::class, to = CartStateMachine::class) {
        bindState<AuthState.SignedIn>  { CartAction.LoadForUser(userId) }
        bindState<AuthState.SignedOut> { CartAction.Clear }
    },
    // Cart effects → Checkout actions
    binder(from = CartStateMachine::class, to = CheckoutStateMachine::class) {
        bindEffect<CartEffect.CartChanged> { CheckoutAction.SyncCart(items, total) }
    },
)

// Register machines (order doesn't matter):
AuthStateMachine(scope, authRepo).register(registry)
CartStateMachine(scope, cartRepo).register(registry)
CheckoutStateMachine(scope, checkoutRepo).register(registry)
```

Each registered machine is automatically wired to matching sources/targets. Binders and machines can be installed in any order.

---

## Android integration

### Lifecycle forwarding

```kotlin
@Composable
fun MyScreen(store: Store<MyState, MyAction, MyEffect>) {
    store.bindLifecycle()     // forwards OnResume/OnPause automatically

    val viewStore = store.toViewStore()
    store.handleEffects { effect ->
        when (effect) {
            is MyEffect.Navigate -> navController.navigate(effect.route)
        }
    }

    // Render state
    viewStore.render<MyState.Active> {
        Text("Active: ${state.data}")
    }
}
```

### ViewModel cleanup

```kotlin
class MyViewModel(repo: MyRepository) : ViewModel() {
    val store = MyStateMachine(viewModelScope, repo).also { bind(it) }
}
```

`bind(store)` registers a `Closeable` that calls `store.cancel()` when the ViewModel is cleared.

---

## Architecture overview

```
┌─────────────────────────────────────────────────────────┐
│  DSL layer                                              │
│  store { }  ·  stateMachine { }  ·  StateMachineStore   │
└───────────────────────┬─────────────────────────────────┘
                        │ builds StateMachine (config)
┌───────────────────────▼─────────────────────────────────┐
│  Runtime layer                                          │
│  DefaultStore  ─────────────────────────────────────    │
│    Channel<Trigger>(UNLIMITED)                          │
│    └─ processingJob (single coroutine)                  │
│         ├─ resolveActionHandler (exact → supertype BFS) │
│         ├─ processTransition                            │
│         │    ├─ onExit hook                             │
│         │    └─ onEnter hook                            │
│         └─ processStateUpdate → onUpdate hook           │
│  JobRegistry  (keyed cancellable jobs)                  │
│  StoreRegistry  (multi-machine coordination)            │
└─────────────────────────────────────────────────────────┘
```

**Key invariants:**
- Actions are processed one at a time — no concurrent state mutations.
- Handler exceptions are caught and forwarded to plugins; the state is not changed.
- Keyed jobs: `launch(key)` auto-cancels the previous job with the same key.
- Plugins are called synchronously in registration order inside the processing coroutine.
- `onExit` fires before `onEnter` on state-type changes.
- `onEnter` does not fire for the initial state.
