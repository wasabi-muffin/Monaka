# StateMachineStore & reusable configs

Monaka provides two patterns for structuring larger machines: **`StateMachineStore`** (a named
class with an inline DSL) and **`stateMachine { }`** (a reusable configuration value). Both
produce the same runtime behaviour — the choice is about code organisation.

---

## Store name

Every store has a `name: String` property. Set it in the builder with `name(…)`:

```kotlin
val store = store<MyState, MyAction, MyEffect>(scope) {
    name("Login")
    initialState(MyState.Idle)
    // …
}
println(store.name)  // "Login"
```

For `StateMachineStore` subclasses and named classes that delegate `StateMachine` by class,
`name` defaults automatically to the subclass's simple class name when not set explicitly:

```kotlin
class LoginStateMachine(…) : StateMachine<…> by stateMachine(builder = { … })
// store(LoginStateMachine(…), scope).name == "LoginStateMachine"

class LoginStore(…) : StateMachineStore<…>(machine, scope)
// loginStore.name == "LoginStore"
```

This makes `name` especially useful for global `StoreRegistry` plugins that want to tag log
output per store:

```kotlin
StoreRegistry(viewModelScope) {
    install { LoggingPlugin(tag = name) }
}
```

---

## `store { }` — inline anonymous machine

The simplest form. The machine lives entirely inside the `store` call, typically inside a
ViewModel or composable:

```kotlin
val store = store<CounterState, CounterAction, CounterEffect>(viewModelScope) {
    initialState(CounterState(0))
    state<CounterState> {
        on<CounterAction.Increment> { transition(state.copy(count = state.count + 1)) }
    }
}
```

Good for small, self-contained machines. Not easily testable with `testStore` without
extracting the handlers — see [Testing](testing.md) for why.

---

## `stateMachine { }` — reusable configuration

`stateMachine { }` builds an immutable `StateMachine` snapshot without starting it. Separate
the configuration from execution to:

- Share the same handlers across multiple store instances
- Inject different initial states at start time
- Pass directly to `testStore` in tests

```kotlin
val loginMachineConfig = stateMachine<LoginState, LoginAction, LoginEffect> {
    initialState(LoginState.Idle)
    state<LoginState.Idle> { … }
    state<LoginState.Typing> { … }
}

// Start two independent instances with the same config:
val store1 = store(loginMachineConfig, scope1)
val store2 = store(loginMachineConfig, scope2, initialState = LoginState.Typing("bob"))
```

### Runtime overrides

Both `store { }` and `store(config, scope)` accept `initialState` and `plugins` overrides that
apply at runtime, after the builder block has run:

```kotlin
val store = store(
    stateMachine = loginMachineConfig,
    scope = viewModelScope,
    initialState = savedStateHandle.get<LoginState>("state"),  // replaces initialState(…) in config
    plugins = listOf(analyticsPlugin),                          // appended after config's plugins
)
```

`extraBufferCapacity` (default: `DEFAULT_BUFFER_CAPACITY` = `64`) controls the `SharedFlow` buffer for effects and actions.
Increase it if your machine emits effects in rapid bursts:

```kotlin
val store = store(
    stateMachine = loginMachineConfig,
    scope = viewModelScope,
    extraBufferCapacity = 128,
)
```

---

## Async state restoration

When persisted state lives in an async source (e.g. Jetpack `DataStore`, a SQLite database, or
encrypted preferences), pass an `initializer` suspend lambda. It runs inside the processing
coroutine — before `onEnter` fires and before any queued actions are processed — so the restored
state is always the first state the machine acts on:

```kotlin
val store = store(
    stateMachine = loginMachineConfig,
    scope = viewModelScope,
    initializer = { dataStore.data.first().toLoginState() },
) {
    initialState(LoginState.Idle)   // used only if initializer is null or throws
}
```

For synchronous sources (`SavedStateHandle`, `UserDefaults`) the existing `initialState`
parameter is simpler and sufficient:

```kotlin
val store = store(
    stateMachine = loginMachineConfig,
    scope = viewModelScope,
    initialState = savedStateHandle.get<LoginState>("state"),
)
```

### Ordering guarantee

Actions dispatched before the initializer completes are queued in the processing channel and run
after `onEnter` has fired with the restored state. No actions are dropped and no action sees the
un-restored placeholder state.

### Error handling

If the initializer throws, `Plugin.onError` is called with `HandlerType.Restore` and the store
falls back to its configured `initialState`. `onEnter` always fires so the machine always
reaches a usable state.

---

## `StateMachine` delegation — named class with injected dependencies

When a machine needs constructor-injected dependencies (repositories, use-cases), declare it as
a named class that implements `StateMachine` via delegation to `stateMachine { }`. The handlers
close over the constructor parameters; the class itself is just an immutable config snapshot:

```kotlin
class LoginStateMachine(
    loginRepository: LoginRepository,
) : StateMachine<LoginState, LoginAction, LoginEffect> by stateMachine(builder = {

    initialState(LoginState.Idle)

    state<LoginState.Typing> {
        on<LoginAction.Submit> {
            task("login", autoCancel = true) {
                when (val result = loginRepository.login(state.username, state.password)) {
                    is Success -> dispatch(LoginAction.LoginSucceeded(result.user))
                    is Failure -> dispatch(LoginAction.LoginFailed(result.message))
                }
            }
            transition(LoginState.Submitting)
        }
    }

    state<LoginState.Submitting> {
        on<LoginAction.LoginSucceeded> {
            transition(LoginState.Authenticated(action.user))
            sideEffect(LoginEffect.NavigateToHome)
        }
        on<LoginAction.LoginFailed> {
            transition(LoginState.Idle)
            sideEffect(LoginEffect.ShowError(action.message))
        }
    }

    state<LoginState> {
        on<LoginAction.Logout> {
            transition(LoginState.Idle)
            sideEffect(LoginEffect.NavigateToLogin)
        }
    }

    install(LoggingPlugin(tag = "Login"))
})
```

Start the machine from a ViewModel using `store(config, scope)` or `StateMachineStore`:

```kotlin
class LoginViewModel(loginRepository: LoginRepository) : ViewModel() {
    private val machine = LoginStateMachine(loginRepository)

    // Option A — store factory
    val store: Store<LoginState, LoginAction, LoginEffect> = store(machine, viewModelScope)

    // Option B — StateMachineStore wrapper (also exposes `store.stateMachine`)
    val store = StateMachineStore(machine, viewModelScope)
}
```

Because `LoginStateMachine` implements `StateMachine`, pass it directly to `testStore`:

```kotlin
@Test
fun loginFlow() = testStore(machine = LoginStateMachine(fakeRepository)) { … }
```

---

## Choosing a pattern

| Pattern | Use when |
|---|---|
| `store { }` inline | Small machine, no testing needed, or machine lives entirely inside a ViewModel. |
| `stateMachine { }` + `store(config, scope)` | You want a reusable config value — multiple instances, passing to `testStore`, or separating config from startup. |
| Named class `: StateMachine<> by stateMachine { }` | The machine has injected dependencies and you want a named, typed class that is also directly passable to `testStore`. |

All three patterns produce a `Store<S, A, E>` — the rest of the API (`state`, `effects`,
`dispatch`, plugins, relays) is identical regardless of which pattern you use.
