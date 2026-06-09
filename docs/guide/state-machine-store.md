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

## `StateMachineStore` — named class with injected dependencies

`StateMachineStore` is a concrete `Store` implementation that delegates to a `StateMachine`
snapshot. It is the recommended base when the machine has constructor-injected dependencies
(repositories, use-cases) and you want to keep the DSL close to its dependencies:

```kotlin
class LoginStateMachine(
    stateMachine: StateMachine<LoginState, LoginAction, LoginEffect>,
    scope: CoroutineScope,
    initialState: LoginState? = null,
    plugins: List<Plugin<LoginState, LoginAction, LoginEffect>> = emptyList(),
) : StateMachineStore<LoginState, LoginAction, LoginEffect>(
    stateMachine = stateMachine,
    scope = scope,
    initialState = initialState,
    plugins = plugins,
)
```

Build the `StateMachine` separately (often as a named function or factory that takes
dependencies as parameters):

```kotlin
fun LoginStateMachine(loginRepository: LoginRepository) =
    stateMachine<LoginState, LoginAction, LoginEffect> {
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
    }
```

Use from a ViewModel:

```kotlin
class LoginViewModel(loginRepository: LoginRepository) : ViewModel() {
    val store = LoginStateMachine(
        stateMachine = LoginStateMachine(loginRepository),
        scope = viewModelScope,
    )
}
```

Because the `StateMachine` value is separate, it can be passed directly to `testStore`:

```kotlin
@Test
fun loginFlow() = testStore(machine = LoginStateMachine(fakeRepository)) { … }
```

---

## Choosing a pattern

| Pattern | Use when |
|---|---|
| `store { }` inline | Small machine, no testing needed, or machine lives inside a ViewModel. |
| `stateMachine { }` + `store(config, scope)` | You need multiple instances, want to test with `testStore`, or want to separate configuration from startup. |
| `StateMachineStore` | The machine has injected dependencies and you want a named, typed class as the public API surface. |

All three patterns produce a `Store<S, A, E>` — the rest of the API (`state`, `effects`,
`dispatch`, plugins, relays) is identical regardless of which pattern you use.
