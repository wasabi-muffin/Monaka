# Testing

The `monaka-test` module provides a coroutine-aware DSL for testing state machines in isolation.
It wraps [Turbine](https://github.com/cashapp/turbine) and `kotlinx-coroutines-test`, so tests
run under virtual time with no real delays.

---

## Setup

```kotlin
// build.gradle.kts — add to your test source set
commonTest.dependencies {
    implementation("dev.gmvalentino.monaka:monaka-test:<version>")
    implementation(kotlin("test"))
}
```

The test DSL requires a `StateMachine<S, A, E>` value built with `stateMachine { }`.
If your production code uses `store { }` inline (e.g. inside a ViewModel), mirror the
handlers in a separate `stateMachine { }` value for testing:

```kotlin
// Production — store tied to a scope
class CounterViewModel : ViewModel() {
    val store = store<CounterState, CounterAction, CounterEffect>(viewModelScope) {
        initialState(CounterState(0))
        state<CounterState> {
            on<CounterAction.Increment> { transition(state.copy(count = state.count + 1)) }
        }
    }
}

// Test — same handlers, no scope
private val counterMachine = stateMachine<CounterState, CounterAction, CounterEffect> {
    initialState(CounterState(0))
    state<CounterState> {
        on<CounterAction.Increment> { transition(state.copy(count = state.count + 1)) }
    }
}
```

If your machine is a `StateMachineStore` class that holds a `stateMachine { }` value as a
property, pass that property directly to `testStore`.

---

## Entry point — `testStore`

```kotlin
@Test
fun counterIncrements() = testStore(machine = counterMachine) {
    testCase("increment once") {
        trigger(CounterAction.Increment) {
            expectState<CounterState> { state.count == 1 }
        }
    }
}
```

`testStore` wraps `runTest`, so it inherits virtual time and runs synchronously in CI.

Each `testCase` block receives a **fresh store** built from the same `StateMachine`
configuration, so test cases within one `testStore` call are completely isolated from
each other.

---

## DSL reference

### `given(state)`

Override the machine's initial state. Must be called before the first `trigger`.

```kotlin
testCase("start from Typing") {
    given(LoginState.Typing(username = "alice", password = "secret"))
    trigger(LoginAction.Submit) {
        expectState<LoginState.Submitting>()
    }
}
```

### `trigger(action) { … }`

Dispatch an action and assert on the resulting emissions inside the block.

```kotlin
trigger(LoginAction.Submit) {
    expectState<LoginState.Submitting>()
    expectState<LoginState.Authenticated> { state.username == "alice" }
    expectEffect(LoginEffect.NavigateToHome)
}
```

### `trigger(LifecycleEvent) { … }`

Forward a lifecycle event and assert on emissions.

```kotlin
trigger(LifecycleEvent.OnPause) {
    expectState<TimerState.Paused>()
}
```

### `trigger(StateHook) { … }`

Fire a state lifecycle hook manually — useful for testing `onEnter` / `onExit` / `onUpdate`
without driving a full transition:

```kotlin
trigger(StateHook.OnEnter) {
    expectState<LoginState.Submitting>()
}

trigger(StateHook.OnUpdate(previousState = SearchState(query = ""))) {
    expectAction<SearchAction.ResultsReceived>()
}
```

### `advanceTime(duration) { … }`

Advance virtual time and assert on emissions produced by timed work:

```kotlin
testCase("debounce fires after 300ms") {
    given(SearchState(query = "", isLoading = false))

    trigger(SearchAction.QueryChanged("ko")) {}  // handler launches task with delay(300)

    advanceTime(300.milliseconds) {
        expectAction<SearchAction.ResultsReceived>()
    }
}
```

---

## Assertion methods

All assertions are called inside a `trigger { … }` block on the implicit `AssertScope` receiver.

### States

| Method | Behaviour |
|---|---|
| `expectState(state)` | Assert next state equals `state`. |
| `expectState<T> { predicate }` | Assert next state is an instance of `T` and matches the optional predicate. Access the typed state via `state`. |
| `skipState()` | Consume the next state without asserting. |

```kotlin
expectState(CounterState(count = 1))
expectState<LoginState.Authenticated> { state.username == "alice" }
```

### Effects

| Method | Behaviour |
|---|---|
| `expectEffect(effect)` | Assert next effect equals `effect`. |
| `expectEffect<T> { predicate }` | Assert next effect is an instance of `T` and matches the optional predicate. Access the typed effect via `effect`. |
| `expectNoEffects()` | Assert no effect has been emitted yet. |
| `skipEffect()` | Consume the next effect without asserting. |

### Handler-initiated actions

These assert on actions that a **handler** dispatched internally (via `ActionScope.dispatch` or
from inside `task { }`). Actions dispatched by the test itself are filtered out.

| Method | Behaviour |
|---|---|
| `expectAction(action)` | Assert next handler action equals `action`. |
| `expectAction<T> { predicate }` | Assert next handler action is an instance of `T` matching the optional predicate. |
| `expectNoAction()` | Assert no handler-initiated action has been emitted yet. |
| `skipAction()` | Consume the next handler action without asserting. |

---

## Automatic idle check

At the end of every test case, `testStore` automatically calls `expectIdle()` — asserting that
all three streams (states, effects, handler actions) are fully drained. This catches unexpected
extra emissions.

Opt out for a specific test case when you intentionally leave work pending:

```kotlin
// At declaration time — skip idle check entirely for this case:
testCase("fires and forgets", exhaustive = false) { … }

// At runtime — conditionally skip based on a branch:
testCase("conditional") {
    trigger(SomeAction) { … }
    if (someCondition) finish()  // stops the idle check from running
}
```

---

## Full example — login flow

```kotlin
private val loginMachine = stateMachine<LoginState, LoginAction, LoginEffect> {
    initialState(LoginState.Idle)

    state<LoginState.Idle> {
        on<LoginAction.TypeCredentials> {
            transition(LoginState.Typing(action.username, action.password))
        }
    }

    state<LoginState.Typing> {
        on<LoginAction.Submit> {
            task("login", autoCancel = true) {
                dispatch(LoginAction.LoginSucceeded("alice"))
            }
            transition(LoginState.Submitting)
        }
    }

    state<LoginState.Submitting> {
        on<LoginAction.LoginSucceeded> {
            transition(LoginState.Authenticated(action.username))
            sideEffect(LoginEffect.NavigateToHome)
        }
        on<LoginAction.LoginFailed> {
            transition(LoginState.Idle)
            sideEffect(LoginEffect.ShowError(action.reason))
        }
    }

    state<LoginState> {
        on<LoginAction.Logout> {
            transition(LoginState.Idle)
            sideEffect(LoginEffect.NavigateToLogin)
        }
    }
}

@Test
fun loginFlow() = testStore(machine = loginMachine) {
    testCase("happy-path login") {
        trigger(LoginAction.TypeCredentials("alice", "secret")) {
            expectState<LoginState.Typing> { state.username == "alice" }
        }

        trigger(LoginAction.Submit) {
            expectState<LoginState.Submitting>()
            expectState<LoginState.Authenticated> { state.username == "alice" }
            expectEffect(LoginEffect.NavigateToHome)
        }
    }

    testCase("logout resets to Idle") {
        given(LoginState.Authenticated("alice"))

        trigger(LoginAction.Logout) {
            expectState<LoginState.Idle>()
            expectEffect(LoginEffect.NavigateToLogin)
        }
    }
}
```
