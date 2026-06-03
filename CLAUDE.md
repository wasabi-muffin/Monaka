# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build
./gradlew :sample:androidApp:assembleDebug              # Build the Android APK
./gradlew :monaka:build                                 # Build the library (all targets)
./gradlew :sample:shared:assemble                       # Build the shared CMP module
./gradlew :sample:shared:embedAndSignAppleFrameworkForXcode  # Invoked by Xcode automatically
./gradlew clean

# Lint
./gradlew lint
```

## Architecture

The sample app is grouped under `sample/`:

```
Monaka/
├── monaka/                 KMP MVI library
└── sample/
    ├── shared/             Shared Compose Multiplatform UI + state machines
    ├── androidApp/         Android entry (MainActivity)
    └── iosApp/             Xcode project (SwiftUI shell)
```

| Module / dir | Plugin | Role |
|---|---|---|
| `:monaka` | `kotlin.multiplatform` + `android.library` | KMP MVI library (Android · iOS · JVM) |
| `:sample:shared` | `kotlin.multiplatform` + `android.library` + Compose Multiplatform | Sample UI + state machines, shared between platforms (Android · iOS) |
| `:sample:androidApp` | `android.application` + `kotlin.compose` | Android entry; thin `MainActivity` that calls `App()` from `:sample:shared` |
| `sample/iosApp/` | Xcode project (not a Gradle module) | iOS entry; SwiftUI shell wrapping `MainViewController()` from `:sample:shared`. Runs `./gradlew :sample:shared:embedAndSignAppleFrameworkForXcode` as a build phase. |

### `:monaka` — KMP StateMachine library

**Targets:** `androidTarget`, `iosX64`, `iosArm64`, `iosSimulatorArm64`, `jvm`

All logic lives in `commonMain`. iOS targets share an intermediate `iosMain` source set (created automatically by `applyDefaultHierarchyTemplate()`).

**Package layout:**

```
tech.fika.monaka/
├── core/      Store, State/Action/Effect markers, LifecycleEvent
├── dsl/       @MonakaDsl, StateBuilder, StateMachineBuilder, stateMachine() factory
├── runtime/   DefaultStateMachine — Channel-based sequential action processor
├── plugin/    Plugin interface, LoggingPlugin
└── bridge/    forwardStateTo / forwardEffectsTo / bridgeTo (inter-machine communication)
```

**Key design decisions:**

- **Action processing** — a single `Channel<Trigger>(UNLIMITED)` + one coroutine guarantees sequential, deterministic state transitions even with concurrent `dispatch()` calls.
- **Handler dispatch** — `KClass` lookup: exact `state::class` → `action::class` first, then BFS supertype walk so a `state<ParentState>` block catches actions unhandled by leaf states.
- **Handler API** — handlers are **statements**, not expressions. Each `on<>` / `onEnter` / etc. lambda returns `Unit` and records its outcome by calling `transition { }`, `sideEffect(...)`, or `reject()` on the scope. The runtime snapshots the recorded result via `consumeResult()` after the lambda returns.
- **Effects** — `MutableSharedFlow(extraBufferCapacity = 64)` with no replay; one-shot events not re-delivered to late subscribers.
- **Plugins** — synchronous observers called inside the processing coroutine. Keep them fast; launch coroutines for heavy work.

**DSL surface area:**

```kotlin
val store = store<MyState, MyAction, MyEffect>(scope) {
    initialState(MyState.Idle)

    state<MyState.Idle> {
        on<MyAction.Start> { transition { MyState.Loading } }
    }

    // Catch an action from any substate via the parent sealed interface:
    state<MyState> {
        on<MyAction.Logout> {
            transition { MyState.Idle }
            sideEffect(MyEffect.NavigateToLogin)
        }
    }

    install(LoggingPlugin())
}
```

**Handler verbs** (on `HandlerScope`):
- `transition { newState }` — record the new state. First call wins; later calls in the same handler are silent no-ops.
- `transition(eff1, eff2) { newState }` — sugar for `transition { } + sideEffect(...)`.
- `sideEffect(eff1, eff2)` — append effects; emitted after the state change in call order.
- `reject()` — terminal: marks the action as rejected, suppresses any subsequent verb calls in the same handler.
- `dispatch(action)` / `task { }` / `task("key") { }` / `cancel("key")` — async helpers; all suppressed after `reject()`. Pass `autoCancel = true` to either `task` overload to have the runtime cancel the job on the next state-type change (just before `onExit` fires).

**Calling UseCases/Repositories in handlers:**

Every `on<>` lambda has `ActionScope<State, Action, Effect, SubState, ActionType>` as its implicit receiver, exposing typed `state` and `action`. Two complementary patterns:

```kotlin
// Pattern 1 — inline suspend (blocks the action queue; one request at a time)
on<Submit> {
    val result = loginRepository.login(state.username, state.password)
    when (result) {
        is Success -> transition { LoginState.Authenticated(result.user) }
        is Failure -> transition { LoginState.Error(result.message) }
    }
}

// Pattern 2 — fire-and-dispatch (non-blocking)
// The handler returns immediately; the repo runs in a sibling coroutine
on<Submit> {
    task("login") {
        when (val r = loginRepository.login(state.username, state.password)) {
            is Success -> dispatch(LoginAction.LoginSucceeded(r.user))
            is Failure -> dispatch(LoginAction.LoginFailed(r.message))
        }
    }
    transition { LoginState.Submitting }
}

// Fire-and-forget (no result action needed)
on<Reset> {
    task { analyticsRepo.trackReset() }
    transition { CounterState(0) }
}
```

Dependencies (UseCases, Repositories) are injected via normal closure capture from the enclosing ViewModel or DI scope — no special DI mechanism in the library.

**Dependency catalog:** `gradle/libs.versions.toml`. All new dependencies go there; reference via `libs.<alias>`.

**Package root:** `tech.fika.monaka` (library namespace: `tech.fika.monaka.library`; `:sample:shared` namespace: `tech.fika.monaka.sample`)

### `:sample:shared` — Compose Multiplatform sample module

Targets `androidTarget`, `iosArm64`, `iosSimulatorArm64`. (Intel Mac simulators / `iosX64` were dropped — Compose Multiplatform 1.11+ no longer publishes those binaries; modern Apple-silicon Macs use `iosSimulatorArm64` for the simulator.) UI lives in `commonMain` using JetBrains Compose. Lifecycle observation uses `org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose` (JetBrains' multiplatform fork of AndroidX lifecycle), so `LocalLifecycleOwner` and `LifecycleEventObserver` work directly in commonMain — no `expect`/`actual` indirection needed. `ComposeUIViewController` populates `LocalLifecycleOwner` on iOS automatically.

**Cross-platform helpers in `tech.fika.monaka.sample`:**
- `App()` — root composable with manual `Screen` enum navigation. No external nav library.
- `rememberStore { scope -> ... }` — replaces the Android-only `ViewModel + viewModel()` pattern. Creates a `Store` tied to the composition's coroutine scope; cancels on disposal.
- `BindLifecycle()` — single commonMain composable. Bridges `androidx.lifecycle.Lifecycle.Event` → `tech.fika.monaka.core.LifecycleEvent`.
- `toViewStore()`, `handleEffects { }`, `render<State>()` — small Compose adapters around `Store`. `toViewStore()` uses `collectAsStateWithLifecycle()` so the UI stops collecting when the screen is backgrounded.
- `Format.kt`: `nowMs()` (via `kotlin.time.Clock.System.now()` from the stdlib, brought in alongside `kotlinx-datetime`), `formatRelativeTime()`, `format()`, `padDigits()` — multiplatform replacements for `System.currentTimeMillis`, `SimpleDateFormat`, `String.format`.

### iOS Xcode project

`sample/iosApp/iosApp.xcodeproj` is a minimal Xcode project with one app target. It runs `./gradlew :sample:shared:embedAndSignAppleFrameworkForXcode` as a build phase, which produces `shared.framework` in `sample/shared/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)`. Swift code imports `shared` and calls `MainViewControllerKt.MainViewController()`.

The framework search path in the Xcode project is `$(SRCROOT)/../shared/build/xcode-frameworks/...` — relative to `sample/iosApp/`, that resolves to `sample/shared/build/...` (siblings under `sample/`).

**Bundle ID and signing** are read from `sample/iosApp/Configuration/Config.xcconfig`. Fill in `TEAM_ID=` with your Apple developer team ID before running on a physical device.

### `:monaka-test` — Test DSL for StateMachines

**Targets:** same as `:monaka` (Android · iOS · JVM). Depends on `app.cash.turbine` and `kotlinx-coroutines-test`.

Add to your module's `commonTest` source set:

```kotlin
commonTest.dependencies {
    implementation(project(":monaka-test"))
    implementation(kotlin("test"))
}
```

**Entry point — `testStore`:**

Pass a `StateMachine` instance (built with `stateMachine { }`) and define one or more named test cases. Each test case constructs an isolated `Store` and tears it down automatically.

```kotlin
@Test
fun loginFlow() = testStore(machine = LoginStateMachine(fakeRepo)) {
    testCase("happy-path login") {
        given(LoginState.Typing(username = "alice", password = "secret"))

        trigger(LoginAction.Submit) {
            expectState<LoginState.Submitting>()
            expectState<LoginState.Authenticated> { it.username == "alice" }
            expectEffect(LoginEffect.NavigateToHome)
        }

        trigger(LoginAction.Logout) {
            expectState<LoginState.Idle>()
            expectEffect(LoginEffect.NavigateToLogin)
        }
    }

    testCase("another test case gets a fresh store") { … }
}
```

**DSL reference:**

| Call | Where | Meaning |
|---|---|---|
| `given(state)` | test case body, before first trigger | Override the machine's `initialState` |
| `trigger(action) { … }` | test case body | Dispatch an action; assert in the block |
| `trigger(LifecycleEvent) { … }` | test case body | Forward a lifecycle event; assert in the block |
| `expectState<T> { predicate }` | trigger block | Assert next state is `T` matching optional predicate |
| `expectEffect(e)` | trigger block | Assert next effect equals `e` |
| `expectNoEffects()` | trigger block | Assert no effect is pending |
| `expectAction(a)` | trigger block | Assert next handler-initiated `dispatch(action)` equals `a` |
| `expectNoAction()` | trigger block | Assert no handler-initiated dispatch is pending |
| `finish()` | test case body | Skip the automatic `expectIdle()` for the remainder of this test case |

`expectIdle()` — that all three streams (states, effects, handler actions) are drained — runs **automatically** at the end of every test case. Pass `exhaustive = false` to opt out at declaration time, or call `finish()` mid-body to opt out at runtime:

```kotlin
testCase("non-exhaustive", exhaustive = false) { … }
```

**`Store` vs `StateMachine`:**

`testStore` requires a `StateMachine<S, A, E>` (built with `stateMachine { }`). Classes that delegate from `store(scope, …)` directly (e.g. `CounterStateMachine`) carry a `CoroutineScope` and must be re-expressed as a `stateMachine { }` value for testing:

```kotlin
// In the test file — mirrors the production handlers without the scope
private val counterMachine = stateMachine<CounterState, CounterAction, CounterEffect> {
    initialState(CounterState(count = 0))
    state<CounterState> {
        on<CounterAction.Increment> { transition { state.copy(count = state.count + 1) } }
        …
    }
}

@Test
fun increment() = testStore(machine = counterMachine) { … }
```
