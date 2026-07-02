---
name: monaka-compose
description: >-
  Wire a Monaka Store into Compose Multiplatform UI with the monaka-compose artifact:
  rememberStore, toViewStore, handleEffects, bindLifecycle, render, ViewStore, RenderScope. Use
  when building Compose screens that observe Monaka state/effects, forwarding lifecycle events, or
  replacing the ViewModel pattern on non-Android targets. For the machines themselves see
  monaka-state-machines.
---

# Monaka + Compose Multiplatform

`monaka-compose` provides `@Composable` helpers to observe a `Store` from Compose. Everything lives
in `dev.gmvalentino.monaka.compose` and works in `commonMain` on Android, iOS, and JVM (anywhere
Compose Multiplatform runs). Add the dependency per monaka-setup. Full docs:
https://monaka.gmvalentino.dev/guide/compose/

## The five helpers

| Helper | Signature (as used) | Purpose |
|---|---|---|
| `rememberStore` | `rememberStore { scope -> Store }` | Create a `Store` tied to the composition; canceled on disposal. Replaces ViewModel on non-Android targets. |
| `toViewStore` | `store.toViewStore(): ViewStore<S, A>` | Collect `state` lifecycle-aware into `ViewStore(state, dispatch)`. |
| `handleEffects` | `store.handleEffects { effect -> … }` | Collect one-shot effects lifecycle-aware (buffered — never dropped). |
| `bindLifecycle` | `store.bindLifecycle()` | Forward platform lifecycle → `onResume`/`onPause`/… hooks. |
| `render` | `state.render<T> { renderState }` | Run a block only when the current state is subtype `T`. |

## A complete screen

```kotlin
import dev.gmvalentino.monaka.compose.*

@Composable
fun LoginScreen(store: Store<LoginState, LoginAction, LoginEffect>) {
    store.bindLifecycle() // enables onResume/onPause/... hooks in the machine

    store.handleEffects { effect ->
        when (effect) {
            LoginEffect.NavigateToHome -> navController.navigate("home")
            LoginEffect.NavigateToLogin -> navController.navigate("login")
            is LoginEffect.ShowValidationError -> snackbar.showSnackbar(effect.message)
        }
    }

    val viewStore = store.toViewStore()
    val state = viewStore.state

    Column {
        // render<T> runs its block only when state is that subtype; `renderState` is typed to T
        state.render<LoginState.Typing> {
            TextField(renderState.username, onValueChange = {
                viewStore.dispatch(LoginAction.UpdateCredentials(it, renderState.password))
            })
            Button(onClick = { viewStore.dispatch(LoginAction.Submit) }) { Text("Sign in") }
        }
        state.render<LoginState.Submitting> { CircularProgressIndicator() }
        state.render<LoginState.Error> { Text(renderState.message, color = Color.Red) }
    }
}
```

- `viewStore.state` is the current state; `viewStore.dispatch(action)` sends an action.
- `ViewStore` is `@Immutable` (`state` + `dispatch`), so it's cheap to pass down.
- `render<T> { }` is an extension on the state value; the block's receiver exposes `renderState`
  typed to `T`. Prefer it to a manual `when` for per-substate UI.

## Owning the store in the composition (`rememberStore`)

On Android you can keep using a ViewModel. On iOS/JVM/desktop there's no ViewModel — use
`rememberStore` to tie a store to the composable's lifetime:

```kotlin
@Composable
fun LoginRoute(repository: LoginRepository) {
    val store = rememberStore { scope -> store(LoginStateMachine(repository), scope) }
    LoginScreen(store) // store.stop() is called automatically when this leaves the composition
}
```

`rememberStore`'s factory receives a `CoroutineScope` bound to the composition. Build any `Store`
inside it — an inline `store {}`, `store(machine, scope)`, or your own `Store`-by-delegation class.

## Effects: always use `handleEffects`

Effects are a no-replay `SharedFlow` — collecting `state` first can drop the initial effect.
`handleEffects` starts a dedicated collector into an unbounded buffer immediately and only *drains*
it while the lifecycle is `STARTED`, so effects emitted while backgrounded are delivered in order on
return. Call `handleEffects` before/independently of `toViewStore`; both are safe to call in the
same composable. `handleEffects` and `bindLifecycle` return the store, so they can be chained.

## Lifecycle

`bindLifecycle()` observes `LocalLifecycleOwner` (JetBrains' multiplatform lifecycle) and forwards
each event to the store as a `LifecycleEvent`. `LocalLifecycleOwner` is provided by the host
Activity/Fragment on Android and by `ComposeUIViewController` on iOS — no `expect`/`actual` needed.
Call it once per screen if the machine has `onResume`/`onPause`/etc. hooks.

## Android ViewModel (still fine)

```kotlin
class LoginViewModel(repo: LoginRepository) : ViewModel() {
    val store = store(LoginStateMachine(repo), viewModelScope) // canceled when the VM is cleared
}

@Composable
fun LoginRoute(vm: LoginViewModel = viewModel()) = LoginScreen(vm.store)
```
