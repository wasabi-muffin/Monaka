# Hierarchical states

Monaka supports sealed interface hierarchies out of the box. You can register a handler block for
a parent (sealed) state type to catch actions that are not handled by any of its leaf subtypes.

---

## Catch-all parent blocks

```kotlin
// Handles Logout from *any* LoginState subtype:
state<LoginState> {
    on<LoginAction.Logout> {
        transition(LoginState.Idle)
        sideEffect(LoginEffect.NavigateToLogin)
    }
}

// LoginState.Loading overrides Cancel — takes priority over the parent block:
state<LoginState.Loading> {
    on<LoginAction.Cancel> { transition(LoginState.Idle) }
}
```

**Dispatch priority:** the runtime resolves handlers by walking supertypes via BFS. The most
specific registered type wins. If `LoginState.Loading` registers `on<Cancel>`, that handler is
used when the machine is in `Loading`; the parent `state<LoginState>` block never sees it.

---

## Defining a sealed hierarchy

```kotlin
sealed interface LoginState : State {
    data object Idle : LoginState
    data class Typing(val username: String, val password: String) : LoginState
    data class Submitting(val username: String, val password: String) : LoginState
    data class Authenticated(val user: User) : LoginState
    data class Error(val message: String) : LoginState
}
```

Register leaf-specific blocks first, then the catch-all:

```kotlin
state<LoginState.Submitting> {
    onEnter {
        task("login", autoCancel = true) {
            val result = repo.login(state.username, state.password)
            dispatch(
                if (result is Success) LoginAction.LoginSucceeded(result.user)
                else LoginAction.LoginFailed(result.message)
            )
        }
    }
}

state<LoginState.Error> {
    on<LoginAction.Retry> { transition(LoginState.Idle) }
}

// Catch-all — fires for any subtype that doesn't handle Logout itself:
state<LoginState> {
    on<LoginAction.Logout> {
        transition(LoginState.Idle)
        sideEffect(LoginEffect.NavigateToLogin)
    }
}
```

---

## Nested hierarchies

The BFS supertype walk handles arbitrarily deep sealed hierarchies. Given:

```
AuthState
├── AuthState.SignedOut
└── AuthState.SignedIn
    ├── AuthState.SignedIn.Active
    └── AuthState.SignedIn.Locked
```

An action dispatched while in `AuthState.SignedIn.Active` is resolved in this order:

1. `state<AuthState.SignedIn.Active>` block
2. `state<AuthState.SignedIn>` block
3. `state<AuthState>` block

Register a handler at the level where the behaviour logically belongs.

---

## Using with the Gradle plugin

The [stub generator](../gradle-plugin/stub-generator.md) reads your YAML spec and emits the
correct sealed hierarchy automatically, including `@SelfTransition` on the root interface and
`@Transition(…)` on each leaf, which the KSP processor uses to generate `toXxx()` helper
functions.
