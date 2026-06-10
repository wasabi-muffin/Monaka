# Compose & Multiplatform integration

The helpers described on this page are provided by the `monaka-compose` artifact. Add it to your
module's `build.gradle.kts`:

```kotlin
implementation("dev.gmvalentino.monaka:monaka-compose:<version>")
```

`monaka-compose` brings in `:monaka` transitively and requires
`org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose` — JetBrains' multiplatform fork of
AndroidX lifecycle. `LocalLifecycleOwner`, `collectAsStateWithLifecycle`, and `repeatOnLifecycle`
all work in `commonMain` with no `expect`/`actual` needed.

All helpers live in the `dev.gmvalentino.monaka.compose` package.

---

## `rememberStore` — composition-scoped store

On non-Android targets (or when you prefer the store tied to composition lifetime rather than a
ViewModel), use `rememberStore` to create a store that is cancelled when the composable leaves
the composition:

```kotlin
@Composable
fun CounterScreen() {
    val store = rememberStore { scope ->
        CounterStateMachine(scope, counterRepository)
    }
    // store is cancelled automatically when this composable is disposed
}
```

The factory lambda receives a `CoroutineScope` backed by `rememberCoroutineScope()`. The store
is created once and remembered across recompositions. When the composable is removed from the
tree, `rememberCoroutineScope` cancels its scope, which fires any `invokeOnCompletion` callbacks —
including the auto-unregistration hook installed by `StoreRegistry.register`. A `DisposableEffect`
also calls `stop()` as a belt-and-suspenders measure to cancel the processing coroutine promptly,
but scope cancellation is what drives registry cleanup.

**Android with ViewModel** — on Android, prefer tying the store to `viewModelScope` instead so
it survives configuration changes:

```kotlin
class CounterViewModel : ViewModel() {
    val store = store<CounterState, CounterAction, CounterEffect>(viewModelScope) { … }
}
```

---

## `toViewStore` — lifecycle-aware state collection

Converts a `Store` into a `ViewStore` — a plain data holder with the current state and a
`dispatch` function — using `collectAsStateWithLifecycle` so state collection stops while the
UI is backgrounded:

```kotlin
@Composable
fun CounterScreen(store: Store<CounterState, CounterAction, CounterEffect>) {
    val viewStore = store.toViewStore()

    Text("Count: ${viewStore.state.count}")
    Button(onClick = { viewStore.dispatch(CounterAction.Increment) }) {
        Text("Increment")
    }
}
```

`ViewStore` is `@Immutable`, so Compose skips recomposition when the reference hasn't changed.

---

## `handleEffects` — lifecycle-aware effect collection

Collects one-shot effects in a lifecycle-aware way. A dedicated coroutine **always** collects
from the store's `SharedFlow` into an internal `Channel` — so no effect is lost during
configuration changes — but the `block` lambda only drains that channel while the lifecycle is
at least `STARTED`:

```kotlin
@Composable
fun LoginScreen(
    store: Store<LoginState, LoginAction, LoginEffect>,
    navController: NavController,
) {
    store.handleEffects { effect ->
        when (effect) {
            LoginEffect.NavigateToHome  -> navController.navigate("home")
            LoginEffect.NavigateToLogin -> navController.navigate("login")
            is LoginEffect.ShowError    -> showSnackbar(effect.message)
        }
    }

    val viewStore = store.toViewStore()
    // … render UI
}
```

`handleEffects` returns `this`, so it chains with other extensions:

```kotlin
store
    .bindLifecycle()
    .handleEffects { effect -> … }
    .toViewStore()
    .let { viewStore -> … }
```

---

## `bindLifecycle` — automatic lifecycle forwarding

Observes the platform lifecycle via `LocalLifecycleOwner` and forwards each event to the store
as a `LifecycleEvent`, enabling `onResume { }`, `onPause { }`, and other lifecycle hooks in the
DSL to fire automatically:

```kotlin
@Composable
fun TimerScreen(store: Store<TimerState, TimerAction, TimerEffect>) {
    store.bindLifecycle()

    // TimerState.Running.onPause { dispatch(TimerAction.Pause) } now fires automatically
}
```

`LocalLifecycleOwner` is provided by `ComposeUIViewController` on iOS and by the host
Activity/Fragment on Android — no extra setup needed on either platform.

The observer is removed in `onDispose`, so there are no leaks when the composable is removed.

---

## `render` — inline state rendering

`render<T>` is a non-composable inline extension on the `State` marker interface. It runs `block`
only when the receiver is an instance of `T`, giving you access to the typed state via
`renderState`:

```kotlin
val viewStore = store.toViewStore()

viewStore.state.render<LoginState.Authenticated> {
    Text("Welcome, ${renderState.username}!")
}

viewStore.state.render<LoginState.Error> {
    Text("Error: ${renderState.message}", color = Color.Red)
}
```

This is an alternative to `when (state) { is … }` that avoids needing an exhaustive branch for
every subtype. Use whichever is clearer for your use case.

---

## Full screen example

```kotlin
@Composable
fun LoginScreen(
    store: Store<LoginState, LoginAction, LoginEffect>,
    navController: NavController,
) {
    store
        .bindLifecycle()
        .handleEffects { effect ->
            when (effect) {
                LoginEffect.NavigateToHome  -> navController.navigate("home")
                is LoginEffect.ShowError    -> /* show snackbar */ Unit
            }
        }

    val viewStore = store.toViewStore()
    val state = viewStore.state

    Column {
        state.render<LoginState.Idle> {
            Button(onClick = { viewStore.dispatch(LoginAction.TypeCredentials("", "")) }) {
                Text("Start")
            }
        }

        state.render<LoginState.Typing> {
            TextField(
                value = renderState.username,
                onValueChange = { viewStore.dispatch(LoginAction.TypeCredentials(it, renderState.password)) },
                label = { Text("Username") },
            )
            Button(onClick = { viewStore.dispatch(LoginAction.Submit) }) {
                Text("Login")
            }
        }

        state.render<LoginState.Submitting> {
            CircularProgressIndicator()
        }

        state.render<LoginState.Error> {
            Text("Login failed: ${renderState.message}", color = MaterialTheme.colorScheme.error)
            Button(onClick = { viewStore.dispatch(LoginAction.Retry) }) {
                Text("Retry")
            }
        }
    }
}
```

---

## Android ViewModel + StoreRegistry

To wire a ViewModel-owned store into a `StoreRegistry`, call `store(…, scope = viewModelScope)`
and chain `.register(registry)`. The `register` call attaches an `invokeOnCompletion` handler
so the store is automatically unregistered when `viewModelScope` is cancelled:

```kotlin
class AppViewModel(
    authRepo: AuthRepository,
    cartRepo: CartRepository,
) : ViewModel() {
    val registry = StoreRegistry(viewModelScope)

    val authStore = store(
        stateMachine = AuthStateMachine(authRepo),
        scope = viewModelScope,
        initialState = AuthState.SignedOut,
    ).register(registry)
}
```
