---
name: monaka-testing
description: >-
  Write tests for Monaka state machines with the monaka-test DSL: testStore, testCase, given,
  trigger, expectState/expectEffect/expectAction, expectNoEffects, skip*, advanceTime (virtual
  time), and the automatic exhaustive idle check. Use when adding or fixing tests for Monaka
  stores/state machines. For authoring the machines under test see monaka-state-machines.
---

# Testing Monaka state machines

`monaka-test` is a Turbine-backed DSL for asserting on a machine's state/effect/action streams. It
runs under `runTest`, so `delay`/timeouts use **virtual time** — no real waiting. Full docs:
https://monaka.gmvalentino.dev/guide/testing/

## Dependency

Add to the `commonTest` source set (see monaka-setup for details):

```kotlin
commonTest.dependencies {
    implementation("dev.gmvalentino.monaka:monaka-test:<version>") // same version as monaka
    implementation(kotlin("test"))
}
```

## What you pass to `testStore`

`testStore(machine = …)` needs a **`StateMachine<S, A, E>` value** (built with `stateMachine {}`).

- A **named machine class** that delegates to `stateMachine(builder = { … })` is passed directly —
  inject fakes through its constructor.
- If production code uses an inline `store {}` (e.g. inside a ViewModel), **mirror the same
  handlers** in a `stateMachine {}` value in the test file — it carries no `CoroutineScope`.

```kotlin
private val counterMachine = stateMachine<CounterState, CounterAction, CounterEffect> {
    initialState(CounterState(0))
    state<CounterState> {
        on<CounterAction.Increment> { transition(state.copy(count = state.count + 1)) }
    }
}
```

## Anatomy

```kotlin
import dev.gmvalentino.monaka.test.testStore
import kotlin.test.Test

private class FakeLoginRepository(
    private val result: Result<String> = Result.success("alice"),
) : LoginRepository {
    override suspend fun login(username: String, password: String): String = result.getOrThrow()
}

class LoginStateMachineTest {

    @Test
    fun happyPath() = testStore(machine = LoginStateMachine(FakeLoginRepository())) {
        testCase("Typing → Submitting → Authenticated") {
            given(LoginState.Typing(username = "alice", password = "secret"))

            trigger(LoginAction.Submit) {
                expectState<LoginState.Submitting>()
                expectState<LoginState.Authenticated> { state.username == "alice" }
                expectEffect(LoginEffect.NavigateToHome)
            }
        }

        testCase("each test case gets a fresh store") {
            given(LoginState.Typing("bob", "pw"))
            trigger(LoginAction.Submit) { expectState<LoginState.Submitting>() }
        }
    }
}
```

Each `@Test` calls `testStore` once and holds one or more `testCase` blocks; every `testCase`
builds and tears down its **own** `Store`, so cases are isolated.

## DSL reference

| Call | Where | Meaning |
|---|---|---|
| `given(state)` | before the first `trigger` | Override the machine's `initialState`. |
| `trigger(action) { … }` | test case body | Dispatch an action; assert inside the block. |
| `trigger(LifecycleEvent.OnPause) { … }` | test case body | Forward a lifecycle event. |
| `trigger(StateHook.OnEnter) { … }` | test case body | Fire a state hook (`OnEnter`/`OnExit`/`OnUpdate(previous)`) for the current state. |
| `advanceTime(3.seconds) { … }` | test case body | Advance virtual time (drives `delay`-based tasks/ tickers); no action dispatched. |
| `expectState<T> { … }` / `expectState(value)` | trigger block | Next state is `T` matching the predicate, or equals `value`. |
| `expectEffect<T> { … }` / `expectEffect(value)` | trigger block | Next effect is `T` / equals `value`. |
| `expectAction<T> { … }` / `expectAction(value)` | trigger block | Next **handler-initiated** dispatch (from `dispatch(...)` or `task {}`). Test-issued actions are filtered out. |
| `expectNoEffects()` / `expectNoAction()` | trigger block | Assert that stream has nothing pending right now. |
| `skipState()` / `skipEffect()` / `skipAction()` | trigger block | Consume the next emission without asserting. |
| `finish()` | test case body | Skip the automatic idle check for the rest of this case. |

**Predicate receivers.** Inside `expectState<T> { … }` the receiver exposes `state` (typed `T`);
inside `expectEffect<T>` it exposes `effect`; inside `expectAction<T>` it exposes `action`. Write
`expectState<LoginState.Authenticated> { state.username == "alice" }` — **not** `it.username`.

## The automatic idle check (exhaustiveness)

At the end of every `testCase`, monaka-test asserts that **all three streams** (states, effects,
handler actions) are drained — nothing was emitted that you didn't assert on. This catches
accidental extra transitions or effects. Two escape hatches:

```kotlin
testCase("leaves a poll running", exhaustive = false) { … } // opt out up front

testCase("conditionally leaves work") {
    trigger(FeedAction.GoLive) { expectState<FeedState.Live>() }
    finish() // opt out at runtime (e.g. an onEnter started an infinite poll loop)
}
```

Because ordering is asserted, list `expectState`/`expectEffect` in the exact order the machine
emits them — including intermediate states (e.g. `Submitting` then `Authenticated`).

## Common scenarios

**Success vs failure via fakes** — vary the injected repository result:

```kotlin
@Test
fun failedLogin() = testStore(
    machine = LoginStateMachine(FakeLoginRepository(Result.failure(RuntimeException("bad creds")))),
) {
    testCase("Submitting → Error") {
        given(LoginState.Typing("alice", "wrong"))
        trigger(LoginAction.Submit) {
            expectState<LoginState.Submitting>()
            expectState<LoginState.Error> { state.message == "bad creds" }
        }
    }
}
```

**Fire-and-dispatch handlers** — assert the follow-up action, then feed it back:

```kotlin
testCase("search dispatches results") {
    trigger(SearchAction.QueryChanged("kotlin")) {
        expectState<SearchState> { state.isLoading }
    }
    advanceTime(300.milliseconds) {
        expectAction<SearchAction.ResultsReceived>() // dispatched from the debounced task
    }
    trigger(SearchAction.ResultsReceived(results)) {
        expectState<SearchState> { !state.isLoading }
    }
}
```

**Timers / polling** — drive with `advanceTime`:

```kotlin
testCase("timer ticks") {
    given(TimerState.Running(elapsed = 0))
    advanceTime(3.seconds) {
        repeat(3) { expectState<TimerState.Running>() }
    }
}
```

**Lifecycle** — `trigger(LifecycleEvent.OnResume) { … }` to exercise `onResume`/`onPause` hooks.

## Tips

- Keep fakes deterministic (fixed results, no real IO); virtual time handles delays.
- Only handler-initiated dispatches surface to `expectAction` — the action you passed to `trigger`
  is filtered out automatically.
- If a test fails on the trailing idle check, you either forgot to assert an emission or the
  machine did more than intended — check the reported pending item.
