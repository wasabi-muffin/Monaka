# FAQ & Troubleshooting

---

## My `onEnter` for the initial state never fires

`onEnter` is **not** called for the initial state when a store is first constructed. The
processing loop fires it lazily the first time a collector subscribes to `state`, `actions`, or
`effects` — which is normally fine for UI-driven machines because the screen subscribes
immediately.

If you need `onEnter` to fire before any collector subscribes (e.g. in a background ViewModel),
call `store.start()` explicitly after construction:

```kotlin
val store = store<MyState, MyAction, MyEffect>(scope) { … }
store.start()   // fires onEnter for the initial state immediately
```

Calling `start()` more than once is a safe no-op.

---

## Effects are missed by a late subscriber

Effects are emitted on a `SharedFlow` with `replay = 0` — they are one-shot and not
re-delivered to late subscribers. A subscriber that attaches after an effect was emitted will
never receive it.

**In Compose**, use `handleEffects { }` from `monaka-compose`. It buffers every effect into an
internal `Channel` from the moment the composable appears, and drains that channel only when the
lifecycle is `STARTED`. Effects emitted during configuration changes are buffered and delivered
when the screen comes back to the foreground.

**In non-Compose code**, set up your effect collector before dispatching the first action, or
before calling `store.start()`.

---

## The stub generator skips a file that already exists

By default `generateMonakaStubs` does not overwrite existing files (`replace = false`). This is
intentional: the generated stubs are a starting point, and you own them once generated.

To overwrite, pass `--replace=true` on the command line or set it in the extension:

```bash
./gradlew generateMonakaStubs --replace=true
```

```kotlin
monakaStubGenerator {
    replace.set(true)
}
```

---

## My state machine handler is never called

Check the following in order:

1. **Is a `state<T>` block registered for the current state?**  
   The runtime does an exact `state::class` match first, then a BFS supertype walk over
   registered state classes. If no registered class matches the current state (including via
   supertype), the action is rejected.

2. **Is the action type registered under the correct `state<T>` block?**  
   `on<ActionType>` handlers are scoped to the `state<T>` block they are declared in. An action
   dispatched while in `LoginState.Authenticated` will not match an `on<>` in
   `state<LoginState.Idle>`.

3. **Did a parent block already handle the action?**  
   If you have both `state<LoginState>` and `state<LoginState.Loading>` registered, and
   `LoginState` handles the action first (because it was registered before the leaf state), the
   leaf handler is never reached. Register leaf states **before** parent catch-all blocks.

4. **Did a previous handler call `reject()`?**  
   `reject()` is terminal — the action is dropped and no further processing occurs.

Enable `LoggingPlugin` to trace what the runtime is actually doing:

```kotlin
install(LoggingPlugin(tag = "Debug"))
```

---

## `dispatch()` inside a handler doesn't process immediately

`dispatch()` enqueues the action at the back of the `Channel`. Because the processing coroutine
handles one trigger at a time, the dispatched action is processed **after** the current handler
finishes. This is by design — it prevents re-entrant state mutations and keeps transitions
deterministic.

If you need the result of the dispatched action before the handler returns, restructure the
handler to perform the work inline (the blocking pattern) or split it across two handlers using
the fire-and-dispatch pattern.

---

## Multiple `transition()` calls — only the first takes effect

`transition()` is first-write-wins. Calling it a second time in the same handler is a silent
no-op. This is intentional — it enables fallback patterns:

```kotlin
on<Refresh> {
    if (state.isStale) transition(Refreshing)
    transition(Active)   // no-op if already called above
}
```

If you want mutually exclusive branches, use `if/else` so only one `transition()` call is
reachable.

---

## `onUpdate` fires when I don't expect it to

`onUpdate` fires when the state **type** stays the same but the **value** changes — this
includes any field update on a data class, even one you don't care about.

Use the `fromState` / `toState` accessors to guard against unwanted reactions:

```kotlin
state<SearchState> {
    onUpdate {
        if (fromState.query == toState.query) return@onUpdate  // only care about query changes
        task { analytics.track(toState.query) }
    }
}
```

---

## `StoreRegistry` throws on duplicate registration

Registering the same store instance (same `id`) twice throws `IllegalArgumentException`. This
usually happens when a ViewModel is recreated and tries to re-register a store that was not
unregistered from the previous instance.

Use `register` — it attaches an `invokeOnCompletion` callback that automatically unregisters
the store when the owning scope is canceled, so no manual cleanup is needed when the ViewModel
is cleared:

```kotlin
AuthStore(authMachine, viewModelScope).register(registry)
```

If you stop a store explicitly before its scope is canceled, unregister it manually:

```kotlin
store.stop()
registry.unregister(store)
```

---

## `testStore` fails with "no initial state" 

`testStore` requires a `StateMachine<S, A, E>` built with `stateMachine { }` that has
`initialState(…)` set. If you omit `initialState` and don't call `given(state)` in the test
case, the store has no starting state and the test fails at startup.

Either set `initialState` in the machine config:

```kotlin
val machine = stateMachine<MyState, MyAction, MyEffect> {
    initialState(MyState.Idle)   // ← required
    …
}
```

Or call `given(state)` at the start of each test case that needs a specific starting point:

```kotlin
testCase("starts in Loading") {
    given(MyState.Loading)
    …
}
```
