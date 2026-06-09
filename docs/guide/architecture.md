# Architecture

This page describes the internal design of the Monaka runtime for readers who want to understand
_why_ the library behaves the way it does, or who are building plugins, relays, or custom
`Relay` implementations.

---

## Overview

```
┌─────────────────────────────────────────────────────────┐
│  DSL layer                                              │
│  store { }  ·  stateMachine { }  ·  StateMachineStore   │
└───────────────────────┬─────────────────────────────────┘
                        │ builds StateMachine (config)
┌───────────────────────▼─────────────────────────────────┐
│  Runtime layer                 DefaultStore              │
│                                                         │
│   Channel<Trigger>(UNLIMITED)                           │
│   └─ processingJob (single coroutine)                   │
│        ├─ processAction                                 │
│        │    └─ resolveActionHandler (exact → BFS)       │
│        ├─ processLifecycleEvent                         │
│        └─ processStateHook                              │
│                                                         │
│   JobRegistry  (keyed cancellable tasks)                │
│   StoreRegistry  (multi-machine coordination)           │
└─────────────────────────────────────────────────────────┘
```

---

## Single-coroutine actor model

Every `Store` instance owns one `Channel<Trigger>(UNLIMITED)` and one processing coroutine
(`processingJob`). All inputs — dispatched actions, lifecycle events, and manual hook triggers —
are wrapped in a `Trigger` and sent to this channel.

The processing coroutine consumes triggers **one at a time**, in the order they arrive:

```
dispatch(A) ──┐
dispatch(B) ──┤──▶ Channel<Trigger> ──▶ processingJob
lifecycle   ──┘                         (sequential, deterministic)
```

**Consequences:**

- State transitions are race-free. Calling `dispatch()` from multiple threads or coroutines
  simultaneously is safe — actions are serialised by the channel.
- `dispatch()` is non-suspending. It calls `Channel.trySend`, which succeeds immediately because
  the channel is `UNLIMITED`.
- A long-running inline suspend handler (the blocking pattern) does pause the queue for its
  duration. Use `task { }` (fire-and-dispatch) to keep the queue responsive.

---

## Handler resolution — exact match then BFS

When an action arrives, the runtime resolves the handler using a two-step lookup:

1. **Exact match** — check if `actionHandlers[state::class][action::class]` exists.
2. **Ancestor BFS** — if not found, iterate over registered state classes that are supertypes of
   the current state, in the order they were registered. The first match wins.

The ancestor list for a given state class is computed once and cached, so subsequent lookups for
the same state type are O(1).

**What this means in practice:**

```kotlin
state<LoginState.Loading> {
    on<LoginAction.Cancel> { transition(LoginState.Idle) }  // exact match → always wins
}

state<LoginState> {
    on<LoginAction.Cancel> { … }   // only reached if Loading doesn't match Cancel
    on<LoginAction.Logout>  { … }  // catches Logout from any LoginState subtype
}
```

Register more-specific parent blocks **after** leaf blocks to control priority, since ancestor
classes are scanned in registration order.

---

## State lifecycle hook firing order

After a successful transition, the runtime fires hooks in this order:

```
1. jobRegistry.cancelAutoCancellable()   — cancel tasks launched with autoCancel = true
2. onExit { }   for the old state type
3. onEnter { }  for the new state type
```

When the state type stays the same but the value changes (e.g. a data class field update driven
by an action), `onUpdate { }` fires instead:

```
onUpdate { }  for the current state type (value changed, type unchanged)
```

`onEnter` does **not** fire for the initial state when `store { }` is first created. Call
`store.start()` explicitly if you need the initial `onEnter` to fire — the store does this
automatically when the first collector subscribes to `state`, `actions`, or `effects`.

---

## Effects — `SharedFlow` with buffer

Effects are emitted on a `MutableSharedFlow(extraBufferCapacity = DEFAULT_BUFFER_CAPACITY)` with
`replay = 0`. The same capacity is applied to the actions flow.

- **No replay** — late subscribers miss effects emitted before they started collecting. This is
  intentional: effects are one-shot events (navigate, show toast). Wrap your collector in
  `handleEffects { }` (see [Compose integration](compose.md)) to ensure nothing is missed during
  configuration changes.
- **Buffer** — the default capacity (`DEFAULT_BUFFER_CAPACITY = 64`) prevents the processing
  coroutine from suspending when a collector is slow. Increase it via the `extraBufferCapacity`
  parameter on `store { }` or `StateMachineStore` if your machine emits effects in rapid bursts.

---

## Keyed job lifecycle

`task("key") { }` registers the launched coroutine in a `JobRegistry`. The registry maps string
keys to `Job` instances:

- A new `task("key")` **replaces** the existing job with the same key (cancels the old one first).
- `cancel("key")` cancels and removes the job.
- `autoCancel = true` marks the job as auto-cancellable; the runtime calls
  `jobRegistry.cancelAutoCancellable()` before firing `onExit`, so the job is cancelled
  automatically on every state-type change.
- `cancel()` on the store calls `jobRegistry.cancelAll()`, which cancels all running tasks.

---

## Error handling flow

Exceptions thrown inside a handler or hook are caught by the runtime and **do not change state**.
The recovery path is:

```
handler throws
    ↓
Is an onError { } block registered for the current state?
    ├── yes → run onError { }; if it produces a result, apply it
    │          if onError itself throws → notify plugins, no further recovery
    └── no  → notify plugins via Plugin.onError(…)
```

Register an `onError` block in the DSL when you want to transition to an error state on
exception, rather than silently logging it:

```kotlin
state<MyState.Loading> {
    onEnter {
        task { dispatch(MyAction.DataLoaded(repo.fetch())) }
    }
}

// This block runs if onEnter's task throws:
state<MyState> {
    onError {
        transition(MyState.Error(error.message ?: "Unknown error"))
    }
}
```

`ErrorScope` exposes `error` (the raw `Throwable`), `state` (the current state), and
`handlerType` (which handler produced the error).

---

## `Store` lifecycle

```
Idle ──▶ Running ──▶ Cancelled
```

- **Idle** — the store is constructed but `start()` has not been called. The processing
  coroutine is launched immediately (it blocks on the channel), but the initial `onEnter` hook
  has not fired.
- **Running** — `start()` was called (or a collector subscribed to `state`/`effects`/`actions`).
  The initial state's `onEnter` fires once. All subsequent calls to `start()` are no-ops.
- **Cancelled** — `cancel()` was called or the owning `CoroutineScope` was cancelled. The
  processing coroutine and all keyed jobs are cancelled. The channel is closed. Further calls to
  `dispatch` and `onLifecycleEvent` are silent no-ops.
