# Monaka

A Kotlin Multiplatform MVI state machine library. It provides a type-safe DSL for defining states, actions, effects, and their transitions, backed by a single-coroutine actor model that guarantees deterministic, race-free state updates.

**Targets:** Android · iOS (arm64, x64, Simulator arm64) · JVM · macOS · watchOS · tvOS · Linux · Windows · JS · Wasm

---

## Core concepts

| Concept | Interface | Role |
|---|---|---|
| **State** | `State` | Snapshot of the machine at a point in time |
| **Action** | `Action` | Intent dispatched by the UI or system |
| **Effect** | `Effect` | One-shot side effect (navigation, toast, analytics) |
| **Store** | `Store<S,A,E>` | Running instance; exposes `state`, `actions`, `effects`, `dispatch` |

Effects are one-shot: they are emitted on a `SharedFlow` with no replay, so late subscribers miss past emissions.

---

## Setup

```kotlin
// build.gradle.kts — core library
implementation("dev.gmvalentino:monaka:<version>")

// build.gradle.kts — test DSL (commonTest source set)
testImplementation("dev.gmvalentino:monaka-test:<version>")
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

The same `App()` composable in `sample/shared/src/commonMain/kotlin/dev/gmvalentino/monaka/sample/App.kt` powers both platforms. Lifecycle observation uses `org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose` (JetBrains' multiplatform fork of AndroidX lifecycle) directly in `commonMain` — no `expect`/`actual` needed.

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
        on<CounterAction.Increment> { transition(state.copy(count = state.count + 1)) }
        on<CounterAction.Decrement> { transition(state.copy(count = state.count - 1)) }
        on<CounterAction.Reset> {
            transition(CounterState(0))
            sideEffect(CounterEffect.Saved)
        }
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
        is Success -> transition(LoginState.Authenticated(result.user))
        is Failure -> transition(LoginState.Error(result.message))
    }
}
```

### Fire-and-dispatch (non-blocking)

Return immediately and let the async work dispatch a follow-up action when done.

```kotlin
on<LoginAction.Submit> {
    task("login") {
        val r = loginRepository.login(state.username, state.password)
        dispatch(if (r is Success) LoginAction.LoginSucceeded(r.user) else LoginAction.LoginFailed(r.message))
    }
    transition(LoginState.Submitting)
}
```

### Keyed jobs (debounce / cancel)

`task(key)` cancels any running job with the same key before starting a new one.

```kotlin
on<SearchAction.QueryChanged> {
    task("search") {
        delay(300)
        dispatch(SearchAction.ResultsReceived(repository.search(action.query)))
    }
    transition(state.copy(query = action.query, isLoading = true))
}

on<SearchAction.Clear> {
    cancel("search")
    transition(SearchState.Idle)
}
```

---

## Handler verbs

Handlers are **statements**, not expressions — call these methods on the scope to record what
the runtime should do. The lambda itself returns `Unit`; doing nothing is a silent no-op.

| Verb | Behaviour |
|---|---|
| `transition(newState)` | Record the new state. **First call wins** — later calls in the same handler are ignored. |
| `sideEffect(effect1, effect2)` | Append effects to be emitted in call order, after the state change (if any). |
| `reject()` | Mark the action as rejected. **Terminal** — subsequent `transition`, `sideEffect`, `dispatch`, `task`, and `cancel` calls become no-ops. Plugins notified via `onRejected`. |
| `guard { predicate }` | Short-circuit all subsequent verbs if `predicate` returns false. Unlike `reject()`, pre-guard recordings are preserved and plugins are not notified. |
| `dispatch(action)` | Enqueue an action for later processing. |
| `task { }` / `task("key") { }` | Fire-and-forget coroutine (optionally keyed for cancellation/debounce). |
| `cancel("key")` | Cancel the keyed coroutine. |

### First-write-wins for `transition`

Use it for fallback patterns:

```kotlin
on<Refresh> {
    if (state.isStale) transition(Refreshing)
    transition(Active)   // fallback when not stale — ignored if transition was already called
}
```

If you genuinely want exclusive selection, use `if/else` so the second branch is unreachable.

### Terminal `reject`

```kotlin
on<Submit> {
    if (!state.isValid) { reject(); return@on }
    transition(Submitting)
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
        task("poll") {
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
        if (fromState.query != state.query) task { analytics.track(state.query) }
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
        transition(LoginState.Idle)
        sideEffect(LoginEffect.NavigateToLogin)
    }
}

// But Loading has its own Cancel handler that takes precedence:
state<LoginState.Loading> {
    on<LoginAction.Cancel> { transition(LoginState.Idle) }
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
    override fun onTransition(fromState: MyState, toState: MyState) {
        analytics.track("transition", mapOf("from" to fromState, "to" to toState))
    }
    override fun onError(error: Throwable, currentState: MyState, handlerType: HandlerType<MyAction>) {
        crashReporter.log(error)
    }
}
```

---

## Named-class machines

For larger machines with injected dependencies, implement `StateMachine` via delegation to `stateMachine { }`. The handlers close over the constructor parameters; the class is an immutable config snapshot that can be passed directly to `testStore`:

```kotlin
class LoginStateMachine(
    private val repo: LoginRepository,
) : StateMachine<LoginState, LoginAction, LoginEffect> by stateMachine(builder = {

    initialState(LoginState.Idle)

    state<LoginState.Idle> {
        on<LoginAction.TypeCredentials> {
            transition(LoginState.Typing(action.username, action.password))
        }
    }

    state<LoginState.Typing> {
        on<LoginAction.Submit> { transition(LoginState.Submitting(state.username, state.password)) }
    }

    state<LoginState.Submitting> {
        onEnter {
            val result = repo.login(state.username, state.password)
            when (result) {
                is Success -> {
                    transition(LoginState.Authenticated(result.user))
                    sideEffect(LoginEffect.NavigateToHome)
                }
                is Failure -> transition(LoginState.Error(result.reason))
            }
        }
    }

    state<LoginState> {
        on<LoginAction.Logout> {
            transition(LoginState.Idle)
            sideEffect(LoginEffect.NavigateToLogin)
        }
    }

    install(LoggingPlugin(tag = "Login"))
})
```

Start it from a ViewModel using `store(machine, scope)`:

```kotlin
class LoginViewModel(repo: LoginRepository) : ViewModel() {
    private val machine = LoginStateMachine(repo)
    val store = store(machine, viewModelScope)
}
```

Pass the same `LoginStateMachine` directly to `testStore` in tests:

```kotlin
@Test
fun loginFlow() = testStore(machine = LoginStateMachine(fakeRepo)) { … }
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

Use `StoreRegistry` and `relay` to wire machines together declaratively.

```kotlin
val registry = StoreRegistry(viewModelScope)

// Auth state → Cart and Checkout actions
registry.bind(
    relay(from = AuthStore::class) {
        state<AuthState.SignedIn>  { dispatch(CartStore::class, CartAction.LoadForUser(event.user.id)) }
        state<AuthState.SignedOut> {
            dispatch(CartStore::class, CartAction.Clear)
            dispatch(CheckoutStore::class, CheckoutAction.Cancel)
        }
    },
    // Cart effects → Checkout actions
    relay(from = CartStore::class) {
        effect<CartEffect.CartChanged> {
            dispatch(CheckoutStore::class, CheckoutAction.SyncCart(event.items, event.total))
        }
    },
)

// Register machines (order doesn't matter):
AuthStore(authMachine, scope).register(registry)
CartStore(cartMachine, scope).register(registry)
CheckoutStore(checkoutMachine, scope).register(registry)
```

Each registered machine is automatically wired to matching sources/targets. Relays and machines can be installed in any order.

---

## Compose / multiplatform integration

These helpers live in `sample/shared` and are available as patterns to copy into your own project.

### Lifecycle forwarding

```kotlin
@Composable
fun MyScreen(store: Store<MyState, MyAction, MyEffect>) {
    store.bindLifecycle()     // forwards OnCreate/OnResume/OnPause/… automatically

    val viewStore = store.toViewStore()
    store.handleEffects { effect ->
        when (effect) {
            is MyEffect.Navigate -> navController.navigate(effect.route)
        }
    }

    // Render state — render is an extension on StateMarker
    viewStore.state.render<MyState.Active> {
        Text("Active: ${renderState.data}")
    }
}
```

### Composition-scoped store (replaces ViewModel on non-Android targets)

```kotlin
@Composable
fun MyScreen() {
    val store = rememberStore { scope ->
        MyStateMachine(scope, myRepository)
    }
    // store is tied to the composition — cancelled when the composable leaves
}
```

### ViewModel cleanup (Android)

```kotlin
class MyViewModel(repo: MyRepository) : ViewModel() {
    val store = store<MyState, MyAction, MyEffect>(viewModelScope) {
        initialState(MyState.Idle)
        // …
    }
}
```

The store is automatically cancelled when `viewModelScope` is cleared.

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
│         ├─ resolveActionHandler (exact → ancestor, reg. order) │
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
- Keyed jobs: `task(key)` auto-cancels the previous job with the same key.
- Plugins are called synchronously in registration order inside the processing coroutine.
- `onExit` fires before `onEnter` on state-type changes.
- `onEnter` does not fire for the initial state.
