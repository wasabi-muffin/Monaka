# Login

**Source:** `sample/shared/…/examples/login/`

The login example is a multi-state machine demonstrating the `onEnter` blocking pattern, the
`onError` recovery hook, and generated transition helpers from `@Transition` and
`@SelfTransition`. It also shows how to register a catch-all handler on the parent sealed
interface for actions that are valid from any substate.

---

## Types

```kotlin
@SelfTransition
sealed interface LoginState : State {
    data object Idle : LoginState

    @Transition(Submitting::class)
    data class Typing(val username: String, val password: String) : LoginState {
        val isValid get() = username.isNotBlank() && password.isNotBlank()
    }

    @Transition(Authenticated::class, Error::class)
    data class Submitting(val username: String, val password: String) : LoginState

    data class Authenticated(val username: String) : LoginState

    data class Error(val message: String, val username: String, val password: String) : LoginState
}

sealed interface LoginAction : Action {
    data class UpdateCredentials(val username: String, val password: String) : LoginAction
    data object Submit : LoginAction
    data object Retry  : LoginAction
    data object Logout : LoginAction
}

sealed interface LoginEffect : Effect {
    data object NavigateToHome  : LoginEffect
    data object NavigateToLogin : LoginEffect
    data class  ShowValidationError(val message: String) : LoginEffect
}
```

---

## State machine

```kotlin
class LoginStateMachine(loginRepository: LoginRepository) :
    StateMachine<LoginState, LoginAction, LoginEffect> by stateMachine(builder = {

    initialState(LoginState.Idle)

    state<LoginState.Idle> {
        on<LoginAction.UpdateCredentials> {
            transition(state.toTyping(username = action.username, password = action.password))
        }
    }

    state<LoginState.Typing> {
        on<LoginAction.UpdateCredentials> {
            transition(state.copy(username = action.username, password = action.password))
        }
        on<LoginAction.Submit> {
            if (!state.isValid) {
                sideEffect(LoginEffect.ShowValidationError("Please fill in all fields."))
            } else {
                transition(state.toSubmitting())
            }
        }
    }

    state<LoginState.Submitting> {
        onEnter {
            // Blocking pattern: the action queue pauses until this returns.
            val username = loginRepository.login(state.username, state.password)
            transition(state.toAuthenticated(username = username))
            sideEffect(LoginEffect.NavigateToHome)
        }
        onError {
            transition(state.toError(message = error.message ?: "Login failed"))
        }
    }

    state<LoginState.Error> {
        on<LoginAction.UpdateCredentials> {
            transition(state.copy(username = action.username, password = action.password))
        }
        on<LoginAction.Retry> {
            transition(state.toSubmitting())
        }
    }

    // Logout is valid from *any* LoginState subtype.
    state<LoginState> {
        on<LoginAction.Logout> {
            transition(LoginState.Idle)
            sideEffect(LoginEffect.NavigateToLogin)
        }
    }

    install(LoggingPlugin(tag = "Login"))
})
```

---

## Patterns demonstrated

### `onEnter` blocking pattern

`Submitting.onEnter` calls the repository directly as a suspend function. The action queue is
paused while the coroutine suspends — nothing else is processed until `login()` returns. This is
the simplest pattern when only one request can be in-flight at a time:

```kotlin
state<LoginState.Submitting> {
    onEnter {
        val username = loginRepository.login(state.username, state.password)
        transition(state.toAuthenticated(username = username))
        sideEffect(LoginEffect.NavigateToHome)
    }
}
```

For non-blocking alternatives, see [Handlers — Fire-and-dispatch](../guide/handlers.md#fire-and-dispatch-non-blocking).

### `onError` recovery

If `loginRepository.login()` throws, the `onError` block catches it and transitions to the
`Error` state. Without `onError`, the exception would be forwarded to plugins and the state
would remain `Submitting` indefinitely:

```kotlin
state<LoginState.Submitting> {
    onError {
        transition(state.toError(message = error.message ?: "Login failed"))
    }
}
```

See [Error handling](../guide/error-handling.md) for full details.

### Generated transition helpers

`@Transition` on `Typing` and `Submitting` causes the KSP processor to generate typed
`toXxx()` functions. Shared properties (same name and type on both source and target) become
defaulted parameters:

```kotlin
// Generated from @Transition(Submitting::class) on Typing:
fun LoginState.Typing.toSubmitting(): LoginState.Submitting =
    LoginState.Submitting(username = this.username, password = this.password)

// Generated from @Transition(Authenticated::class, Error::class) on Submitting:
fun LoginState.Submitting.toAuthenticated(username: String): LoginState.Authenticated = …
fun LoginState.Submitting.toError(message: String): LoginState.Error = …
```

These make handlers concise and refactor-safe — renaming a constructor parameter updates all
generated call sites.

### Catch-all parent block for `Logout`

`Logout` is valid from every substate. Registering it on `state<LoginState>` (the sealed
root) means the handler fires regardless of which subtype the machine is currently in, without
duplicating the handler in every leaf:

```kotlin
state<LoginState> {
    on<LoginAction.Logout> {
        transition(LoginState.Idle)
        sideEffect(LoginEffect.NavigateToLogin)
    }
}
```

Leaf blocks still take priority: if `LoginState.Typing` registered its own `on<Logout>`, that
would win while in `Typing`.
