# Store API reference

`Store<State, Action, Effect>` is the public contract for every running state machine instance,
regardless of how it was created (`store { }`, `StateMachineStore`, etc.).

---

## Properties

### `id: String`

A unique identifier for this store instance. Auto-generated as a UUID by default. Used by
`StoreRegistry` to distinguish multiple instances of the same store class, and by
`RelayScope.dispatch(…, id = …)` to target a specific instance:

```kotlin
// Target one specific CartStore instance out of several registered:
dispatch(CartStore::class, CartAction.Clear, id = specificCartId)
```

You can read the `id` to log or correlate store activity:

```kotlin
install(object : Plugin<MyState, MyAction, MyEffect> {
    override fun onTransition(fromState: MyState, toState: MyState) {
        logger.d("store[$id] $fromState → $toState")
    }
})
```

---

### `state: StateFlow<State>`

The current state, exposed as a `StateFlow`. Always holds a value; the initial emission is the
configured `initialState`.

```kotlin
store.state.collect { state -> render(state) }
```

Collecting `state` also calls `start()` implicitly — the store's `onEnter` for the initial state
fires the first time a subscriber attaches.

---

### `effects: SharedFlow<Effect>`

One-shot side effects, exposed as a `SharedFlow` with `replay = 0`. Late subscribers miss effects
emitted before they subscribed. Use `handleEffects { }` (see
[Compose integration](../guide/compose.md)) or attach your collector before the first dispatch
to avoid missing emissions.

```kotlin
store.effects.collect { effect -> handle(effect) }
```

---

### `actions: SharedFlow<Action>`

Every action dispatched to the store, emitted in dispatch order **before** the action is
processed. `replay = 0` — late subscribers miss past actions.

Primary use-case is relaying: the `relay { action<A> { … } }` DSL subscribes to this flow
internally. You can also use it for debug logging or analytics:

```kotlin
store.actions.collect { action -> logger.d("dispatched: $action") }
```

---

### `isActive: Boolean`

`true` while the store is processing actions; `false` after `stop()` is called or the owning
`CoroutineScope` is cancelled. All write operations (`dispatch`, `onLifecycleEvent`) are silent
no-ops when `isActive` is `false`.

```kotlin
if (store.isActive) {
    store.dispatch(MyAction.Sync)
}
```

---

## Functions

### `dispatch(action: Action)`

Enqueue an action for processing. Non-suspending and safe to call from any thread or coroutine.
Actions are processed sequentially in the order they are enqueued.

```kotlin
button.setOnClickListener { store.dispatch(MyAction.Submit) }
```

---

### `start()`

Fire the `onEnter` hook for the initial state, if one is registered.

When an `initializer` was provided at store construction, `start()` enqueues the async restore
first. The initializer runs inside the processing coroutine before `onEnter` and before any
queued actions, so the machine always sees the restored state as its first state.

`start()` is called automatically the first time a subscriber collects `state`, `actions`, or
`effects`. Call it explicitly when you need `onEnter` to fire before any collector attaches —
for example, in a background `ViewModel` that starts work immediately on creation:

```kotlin
class SyncViewModel : ViewModel() {
    val store = store<SyncState, SyncAction, SyncEffect>(viewModelScope) {
        initialState(SyncState.Idle)
        state<SyncState.Idle> {
            onEnter { dispatch(SyncAction.StartSync) }
        }
    }

    init {
        store.start()   // onEnter fires immediately; no UI subscriber needed
    }
}
```

Calling `start()` more than once is a safe no-op. Calling it after `stop()` is also a no-op.

---

### `stop()`

Stop the store permanently. Cancels the internal processing coroutine and all running keyed jobs.
Closes the trigger channel. All subsequent calls to `dispatch` and `onLifecycleEvent` become
silent no-ops.

Calling `stop()` also fires any callbacks registered via `invokeOnCompletion` — including the
auto-unregistration hook installed by `StoreRegistry.register`. This means you can cleanly tear
down a registry-tracked store without cancelling its owning scope:

```kotlin
// Composition entry leaving the nav stack — stop() unregisters the store from the registry
DisposableEffect(viewModel) {
    onDispose { viewModel.store.stop() }
}
```

On Android, prefer letting the owning `CoroutineScope` (e.g. `viewModelScope`) stop the store
automatically when the ViewModel is cleared. Call `stop()` explicitly only when the store has a
shorter lifetime than its scope.

---

### `onLifecycleEvent(event: LifecycleEvent)`

Forward an application lifecycle event into the machine. The event is enqueued in the same
channel as actions and processed sequentially. See
[Lifecycle hooks](../guide/lifecycle-hooks.md#app-lifecycle-hooks) for the full list of events
and how to react to them in the DSL.

---

### `triggerStateHook(hook: StateHook<State>)`

Fire a state lifecycle hook (`OnEnter`, `OnExit`, or `OnUpdate`) directly, without requiring a
transition. Annotated `@InternalMonakaApi` — calling it outside of test infrastructure requires
`@OptIn(InternalMonakaApi::class)`. In practice this is handled automatically by `:monaka-test`,
which calls it on your behalf via `trigger(StateHook.OnEnter) { … }`. See
[Testing](../guide/testing.md#triggerstatehooke--) for usage.

---

### `invokeOnCompletion(handler: (Throwable?) -> Unit): DisposableHandle`

Register a callback that fires when the store is stopped. The handler receives the cancellation
cause, or `null` for normal completion. Useful for observing store lifetime without holding a
reference to the underlying scope:

```kotlin
val handle = store.invokeOnCompletion { cause ->
    logger.d("store completed, cause=$cause")
}

// Later, if you want to remove the callback:
handle.dispose()
```

The callback fires when **either** of the following occurs:

- The store's owning `CoroutineScope` is cancelled (e.g. `viewModelScope` cleared on Android).
- `stop()` is called explicitly (e.g. from a Compose `DisposableEffect`).

This is how `StoreRegistry.register` implements auto-unregistration: it attaches an
`invokeOnCompletion` handler at registration time, so the store removes itself from the registry
regardless of whether it was stopped by scope cancellation or by a direct `stop()` call.
