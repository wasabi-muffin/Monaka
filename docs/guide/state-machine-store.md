# StateMachineStore & reusable configs

Monaka provides two patterns for structuring larger machines: **`StateMachineStore`** (a named
class with an inline DSL) and **`stateMachine { }`** (a reusable configuration value). Both
produce the same runtime behaviour — the choice is about code organisation.

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

`extraBufferCapacity` (default: `64`) controls the `SharedFlow` buffer for effects and actions.
Increase it if your machine emits effects in rapid bursts:

```kotlin
val store = store(
    stateMachine = loginMachineConfig,
    scope = viewModelScope,
    extraBufferCapacity = 128,
)
```

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
