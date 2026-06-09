# Handlers

Every `on<ActionType>` lambda receives `ActionScope` as its implicit receiver, giving access to
`state` (the current state cast to the registered subtype), `action` (the dispatched action), and
a set of verb methods that record what the runtime should do.

Handlers are **statements**, not expressions — the lambda returns `Unit`. The runtime snapshots
the recorded result after the lambda returns.

---

## Handler patterns

### Inline suspend (blocking)

The handler coroutine suspends until it returns. The action queue is paused for the duration —
exactly one request is processed at a time. Good for simple, fast operations.

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

Return immediately, run work in a sibling coroutine, and dispatch a follow-up action when
complete. The action queue is free to process other actions while the task runs.

```kotlin
on<LoginAction.Submit> {
    task("login") {
        val r = loginRepository.login(state.username, state.password)
        dispatch(
            if (r is Success) LoginAction.LoginSucceeded(r.user)
            else LoginAction.LoginFailed(r.message)
        )
    }
    transition(LoginState.Submitting)
}
```

### Keyed jobs (debounce / cancel)

Passing a string key to `task` cancels any running job with the same key before launching a new
one. Use this for debounce, retry, or explicit cancellation from another handler.

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

Pass `autoCancel = true` to have the runtime cancel the job automatically on the next
state-type change, just before `onExit` fires:

```kotlin
on<LoginAction.Submit> {
    task("login", autoCancel = true) {
        // cancelled automatically if the state type changes before this finishes
        dispatch(LoginAction.LoginSucceeded(repo.login(state.username, state.password)))
    }
    transition(LoginState.Submitting)
}
```

---

## Handler verbs

| Verb | Behaviour |
|---|---|
| `transition(newState)` | Record the next state. **First call wins** — subsequent calls in the same handler are silent no-ops. |
| `sideEffect(e1, e2, …)` | Append effects; emitted in call order after the state change. |
| `reject()` | Mark the action as rejected. **Terminal** — all subsequent verb calls become no-ops. Plugins are notified via `onRejected`. |
| `guard { predicate }` | Short-circuit all subsequent verbs if `predicate` returns `false`. Unlike `reject()`, pre-guard recordings are preserved and plugins are not notified. |
| `dispatch(action)` | Enqueue an action on the store's channel for later processing. |
| `task { }` / `task("key") { }` | Launch a fire-and-forget coroutine, optionally keyed for cancellation. |
| `cancel("key")` | Cancel the running job with the given key. No-op if no job is running. |

### First-write-wins for `transition`

Use fallback patterns without `if/else`:

```kotlin
on<Refresh> {
    if (state.isStale) transition(Refreshing)
    transition(Active)   // ignored if transition() was already called above
}
```

If you need exclusive selection, use `if/else` so only one branch is reachable.

### Terminal `reject`

```kotlin
on<Submit> {
    if (!state.isValid) { reject(); return@on }
    transition(Submitting)
    sideEffect(Analytics.Started)
}
```

Once `reject()` is called, the runtime treats the action as rejected regardless of what was
recorded before it. The state is not changed and effects are not emitted.

### `guard` — conditional short-circuit

`guard { predicate }` stops recording further results if `predicate` returns `false`. Unlike
`reject()`, any verbs called **before** the guard are preserved and will be applied — and
plugins are **not** notified. Use it when partial work should still take effect even if the
main transition shouldn't happen:

```kotlin
on<MyAction.Submit> {
    sideEffect(MyEffect.Analytics)   // always emitted — recorded before the guard
    guard { state.isValid }          // short-circuits everything below when invalid
    transition(MyState.Submitting)
    sideEffect(MyEffect.HideKeyboard)
}
```

When `state.isValid` is `false`: only `Analytics` is emitted; no transition, no `HideKeyboard`.
When `state.isValid` is `true`: all three verbs apply.

Compare with `reject()`, which discards **everything** (including pre-reject side effects) and
notifies plugins:

| | Pre-call verbs | Plugins notified |
|---|---|---|
| `guard { false }` | **preserved** | no |
| `reject()` | discarded | yes (`onRejected`) |

Calling `reject()` after a failing `guard` is a no-op — guard semantics take precedence.
