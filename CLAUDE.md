# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build
./gradlew assembleDebug              # Build app debug APK
./gradlew :monaka:build              # Build the library (all targets)
./gradlew clean

# Lint
./gradlew lint
```

## Architecture

Two modules:

| Module | Plugin | Role |
|--------|--------|------|
| `:monaka` | `kotlin.multiplatform` + `android.library` | KMP MVI library |
| `:app` | `kotlin.android` + `android.application` | Demo / consumer app |

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
val machine = store<MyState, MyAction, MyEffect>(scope) {
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
- `dispatch(action)` / `launch { }` / `launch("key") { }` / `cancel("key")` — async helpers; all suppressed after `reject()`.

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
    launch("login") {
        when (val r = loginRepository.login(state.username, state.password)) {
            is Success -> dispatch(LoginAction.LoginSucceeded(r.user))
            is Failure -> dispatch(LoginAction.LoginFailed(r.message))
        }
    }
    transition { LoginState.Submitting }
}

// Fire-and-forget (no result action needed)
on<Reset> {
    launch { analyticsRepo.trackReset() }
    transition { CounterState(0) }
}
```

Dependencies (UseCases, Repositories) are injected via normal closure capture from the enclosing ViewModel or DI scope — no special DI mechanism in the library.

**Dependency catalog:** `gradle/libs.versions.toml`. All new dependencies go there; reference via `libs.<alias>`.

**Package root:** `tech.fika.monaka` (library namespace: `tech.fika.monaka.library` to avoid R class collision with `:app`)
