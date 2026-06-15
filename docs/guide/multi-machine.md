# Multi-machine coordination

When a feature spans several independent state machines, `StoreRegistry` and `relay` provide a
declarative way to wire them together without the machines holding references to each other. All
coupling lives in one place — the coordinator — and each machine stays oblivious to the others.

---

## StoreRegistry

A `StoreRegistry` is a keyed collection of `Store` instances. It has three jobs:

1. **Keying** — stores are stored under their `KClass` so any relay can dispatch into them
   without needing a direct reference.
2. **Relaying** — relays installed via `bind(…)` start observing a source store as soon as
   a matching instance is registered.
3. **Global plugins** — plugins installed via `install { … }` are attached to every store
   currently in the registry and to every store registered in the future.

```kotlin
val registry = StoreRegistry(bridgeScope = viewModelScope)
```

Pass the same scope that owns the stores so relay collector coroutines are cancelled together
with everything else when the ViewModel is cleared.

### Global plugins

Attach a plugin to every store — including stores registered later — using `install`. The
factory lambda runs once per store and receives a `PluginScope` with access to that store:

```kotlin
val registry = StoreRegistry(viewModelScope) {
    install { LoggingPlugin(tag = name) }   // name = best-available store name
}
```

`PluginScope.name` resolves to the store's explicit name (set via `name(…)` in the builder),
then its class simple name, then its `id` as a last resort. Use `name` rather than
`store.name` to get this fallback chain automatically.

To share a single stateless plugin instance across all stores:

```kotlin
StoreRegistry(viewModelScope) {
    +MyStatelessPlugin()
}
```

See [Plugins — Global plugins via StoreRegistry](plugins.md#global-plugins-via-storeregistry) for
the full reference.

---

### Threading

`StoreRegistry` is **not thread-safe**. All calls to `register`, `unregister`, `bind`, and
`get` / `getAll` must be made from the same thread — typically the main thread. Pass a
`bridgeScope` confined to that thread (e.g. `viewModelScope`, which runs on `Dispatchers.Main`)
so relay coroutines also access the registry on the main thread.

---

## relay { }

A relay observes one source store and dispatches actions into target stores held by the registry.
Create one with the `relay` factory:

```kotlin
val authRelay = relay(from = AuthStore::class) {
    state<AuthState.SignedIn> {
        dispatch(CartStore::class, CartAction.LoadForUser(event.user.id))
    }
    state<AuthState.SignedOut> {
        dispatch(CartStore::class, CartAction.Clear)
        dispatch(CheckoutStore::class, CheckoutAction.Cancel)
    }
}
```

Inside each block, `event` holds the typed source event that matched. `dispatch(TargetStore::class, action)`
looks up all registered instances of `TargetStore` in the registry and dispatches the action to
each one.

### Relay triggers

A relay block can react to three kinds of source event:

| Block | Triggers on |
|---|---|
| `state<S> { }` | Every state emission where the current state is an instance of `S`. |
| `effect<E> { }` | Every side effect emitted by the source store that is an instance of `E`. |
| `action<A> { }` | Every action dispatched to the source store that is an instance of `A`. |

```kotlin
relay(from = CartStore::class) {
    // State-based relay
    state<CartState.Empty> {
        dispatch(CheckoutStore::class, CheckoutAction.Cancel)
    }

    // Effect-based relay
    effect<CartEffect.CartChanged> {
        dispatch(CheckoutStore::class, CheckoutAction.SyncCart(event.items, event.total))
    }

    // Action-based relay (react to an action dispatched to the source)
    action<CartAction.Clear> {
        dispatch(AnalyticsStore::class, AnalyticsAction.TrackCartCleared)
    }
}
```

All matching blocks run for each event — a single relay can drive multiple dispatches per
emission.

### Targeting a specific store instance

When multiple instances of the same store class are registered (e.g. several chat rooms), target
one by its `id`:

```kotlin
state<ChatState.NewMessage> {
    dispatch(NotificationStore::class, NotificationAction.Show(event.message), id = event.roomId)
}
```

---

## Registering stores

Call `register` on a store to add it to the registry. Any installed relay whose source class
matches starts observing immediately, and the store is **automatically stopped and unregistered**
when it is done — no extra cleanup step is needed:

```kotlin
AuthStore(authMachine, viewModelScope).register(registry)
CartStore(cartMachine, viewModelScope).register(registry)
```

Cleanup is triggered when the owning `CoroutineScope` is cancelled (e.g. `viewModelScope`
cleared on Android) — `register` attaches an `invokeOnCompletion` callback that calls `stop()`
and removes the store from the registry when the scope completes.

Calling `store.stop()` directly does **not** trigger the `invokeOnCompletion` hook. If a store
has a shorter lifetime than its scope (e.g. it is driven by a Compose composition rather than a
ViewModel), call `unregister` manually after `stop()`:

```kotlin
// Composition-scoped — manually unregister when the entry leaves the nav stack
DisposableEffect(viewModel) {
    onDispose {
        viewModel.store.stop()
        registry.unregister(viewModel.store)
    }
}
```

To unregister without stopping the store:

```kotlin
registry.unregister(store)
```

### Querying the registry

Retrieve stores by class or by instance ID:

```kotlin
// First registered instance of the class, or null
val auth: Store<AuthState, AuthAction, AuthEffect>? = registry.get(AuthStore::class)

// All registered instances of the class (snapshot at call time)
val rooms: List<Store<RoomState, RoomAction, RoomEffect>> = registry.getAll(RoomStore::class)

// A specific instance by its Store.id (e.g. a UUID assigned at construction)
val room: Store<RoomState, RoomAction, RoomEffect>? = registry.getById(roomId)

// Check whether any instance of the class is registered
if (AuthStore::class in registry) { … }

// The set of KClasses that have at least one registered instance
val registered: Set<KClass<out Store<*, *, *>>> = registry.keys
```

`get` and `getAll` return a snapshot — mutating the registry afterwards does not affect the
returned list. `getById` performs a linear scan across all registered instances; prefer class-keyed
lookup unless you specifically need an instance by UUID.

Relays and stores can be installed in any order. If a relay is installed after its source store
is already registered, it starts observing immediately.

---

## Full example — e-commerce checkout

This example is taken from the sample app and models three independent machines wired through a
coordinator:

```
AuthStore   state(SignedIn)   ──▶ CartStore.LoadForUser
            state(SignedOut)  ──▶ CartStore.Clear
            state(SignedOut)  ──▶ CheckoutStore.Cancel

CartStore   effect(CartChanged) ──▶ CheckoutStore.SyncCart

CheckoutStore state(Done)    ──▶ CartStore.Clear
```

**Declare relays as objects for readability:**

```kotlin
object AuthRelay : Relay<AuthState, AuthAction, AuthEffect> by relay(from = AuthStore::class, builder = {
    state<AuthState.SignedIn> {
        dispatch(CartStore::class, CartAction.LoadForUser(event.user.id))
    }
    state<AuthState.SignedOut> {
        dispatch(CartStore::class, CartAction.Clear)
        dispatch(CheckoutStore::class, CheckoutAction.Cancel)
    }
})

object CartRelay : Relay<CartState, CartAction, CartEffect> by relay(from = CartStore::class, builder = {
    effect<CartEffect.CartChanged> {
        dispatch(CheckoutStore::class, CheckoutAction.SyncCart(event.items, event.total))
    }
})

object CheckoutRelay : Relay<CheckoutState, CheckoutAction, CheckoutEffect> by relay(from = CheckoutStore::class, builder = {
    state<CheckoutState.Done> {
        dispatch(CartStore::class, CartAction.Clear)
    }
})
```

**Wire everything in a coordinator:**

```kotlin
class AppCoordinator(
    authRepository: AuthRepository,
    cartRepository: CartRepository,
    paymentRepository: PaymentRepository,
    scope: CoroutineScope,
) {
    val registry = StoreRegistry(bridgeScope = scope)

    init {
        registry.bind(AuthRelay, CartRelay, CheckoutRelay)

        AuthStore(AuthStateMachine(authRepository), scope).register(registry)
        CartStore(CartStateMachine(cartRepository), scope).register(registry)
        CheckoutStore(CheckoutStateMachine(paymentRepository), scope).register(registry)
    }
}
```

**Use from a ViewModel:**

```kotlin
class AppViewModel(
    authRepo: AuthRepository,
    cartRepo: CartRepository,
    paymentRepo: PaymentRepository,
) : ViewModel() {
    private val coordinator = AppCoordinator(authRepo, cartRepo, paymentRepo, viewModelScope)

    val authStore     get() = coordinator.registry.get(AuthStore::class)
    val cartStore     get() = coordinator.registry.get(CartStore::class)
    val checkoutStore get() = coordinator.registry.get(CheckoutStore::class)
}
```

---

## Relay handler skipping

When a target store class has no registered instances, the relay **skips its handler** for
that emission rather than calling `dispatch` into the void. The collector coroutine itself
keeps running; the handler resumes firing on the next emission once any declared target class
is registered again.

The registry tracks which target classes each relay dispatches to automatically — no extra
configuration is needed. The first time a relay fires and calls `dispatch(TargetStore::class, …)`,
`TargetStore::class` is recorded in the relay's internal target set. From that point on, every
emission checks `targets.any { it in registry }` before invoking the handler. If no target class
has a registered instance the handler body is skipped entirely.

Custom `Relay` implementations that do not override `Relay.targets` retain the previous
behaviour: the handler always fires regardless of whether any target is currently registered.

---

## Implementing `Relay` directly

The `relay { }` DSL covers most cases. When you need injected dependencies or more complex
coordination logic, implement the `Relay` interface directly:

```kotlin
class AuthRelay(
    private val analytics: AnalyticsClient,
) : Relay<AuthState, AuthAction, AuthEffect> {
    override val source = AuthStore::class

    // Declare targets so the registry can suspend/resume this relay automatically
    // when CartStore instances are added or removed.
    override val targets: Set<KClass<out Store<*, *, *>>> = setOf(CartStore::class)

    override fun apply(
        source: Store<*, *, *>,
        registry: StoreRegistry,
        scope: CoroutineScope,
    ): List<Job> = listOf(
        scope.launch {
            (source as AuthStore).state.collect { state ->
                if (state is AuthState.SignedIn) {
                    analytics.track("signed_in", state.user.id)
                    registry.getAll(CartStore::class).forEach { it.dispatch(CartAction.LoadForUser(state.user.id)) }
                }
            }
        }
    )
}
```

Override `targets` with the store classes your `apply` implementation dispatches to. The
handler-skipping guard in `DefaultRelay` uses this set automatically for DSL-built relays; for
custom implementations you are responsible for reading `targets` (or the registry directly)
inside your own collector.
