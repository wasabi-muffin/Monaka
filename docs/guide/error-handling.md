# Error handling

By default, an exception thrown inside a handler or hook is caught by the runtime, the state is
left unchanged, and all installed plugins are notified via `Plugin.onError`. The `onError { }`
hook gives you a place to react — transition to an error state, emit an effect, or dispatch a
retry action — instead of silently swallowing the exception.

---

## `onError { }` hook

Declare `onError` inside a `state<T>` block. It fires whenever **any** handler or hook
registered for that state type throws an unhandled exception:

```kotlin
state<LoginState.Submitting> {
    onEnter {
        val username = loginRepository.login(state.username, state.password)
        transition(state.toAuthenticated(username = username))
        sideEffect(LoginEffect.NavigateToHome)
    }

    onError {
        transition(state.toError(message = error.message ?: "Unknown error"))
    }
}
```

The `onError` lambda runs with `ErrorScope` as its implicit receiver, which exposes the same
verbs as any other handler (`transition`, `sideEffect`, `dispatch`, `task`, `cancel`) plus two
extra properties:

| Property | Type | Description |
|---|---|---|
| `error` | `Throwable` | The raw exception thrown by the handler or hook. |
| `handlerType` | `HandlerType<Action>` | Which handler origin threw — see [HandlerType](#handlertype) below. |
| `state` | `SubState` | The current state at the time the error was thrown. |

---

## Scope and inheritance

`onError` obeys the same supertype BFS resolution as action handlers. Register it on a parent
catch-all state block to recover from errors in any substate without repeating the block:

```kotlin
// Handles errors thrown in any LoginState subtype:
state<LoginState> {
    onError {
        transition(LoginState.Error(message = error.message ?: "Something went wrong"))
        sideEffect(LoginEffect.ShowError)
    }
}

// More specific — only handles errors in Submitting:
state<LoginState.Submitting> {
    onError {
        transition(state.toError(message = error.message ?: "Login failed"))
    }
}
```

When the machine is in `LoginState.Submitting` and an error occurs, the runtime uses the
most specific registered `onError` — the `Submitting` block, not the parent `LoginState` block.

---

## Recovery patterns

### Transition to an error state

```kotlin
state<MyState.Loading> {
    onEnter {
        val data = repository.fetch(state.id)   // throws on network failure
        transition(MyState.Loaded(data))
    }

    onError {
        transition(MyState.Error(message = error.message ?: "Load failed"))
    }
}
```

### Emit an effect only

When you want to show a toast without changing state:

```kotlin
state<MyState.Saving> {
    onError {
        sideEffect(MyEffect.ShowToast("Save failed — please try again"))
    }
}
```

### Dispatch a retry action

```kotlin
state<MyState.Uploading> {
    onError {
        if (state.retryCount < 3) {
            dispatch(MyAction.Retry(state.retryCount + 1))
        } else {
            transition(MyState.Error("Max retries exceeded"))
        }
    }
}
```

### Inspect `handlerType` for fine-grained recovery

```kotlin
state<MyState> {
    onError {
        when (handlerType) {
            is HandlerType.Action    -> transition(MyState.Error("Action failed: ${error.message}"))
            is HandlerType.Hook.Enter -> transition(MyState.Error("Entry failed: ${error.message}"))
            else                     -> sideEffect(MyEffect.LogError(error))
        }
    }
}
```

---

## `HandlerType`

`HandlerType` identifies which part of the DSL triggered the error:

| Variant | When |
|---|---|
| `HandlerType.Action(action)` | An `on<>` handler threw. `action` is the dispatched action. |
| `HandlerType.Lifecycle(event)` | An `onPause`, `onResume`, etc. hook threw. `event` is the lifecycle event. |
| `HandlerType.Hook.Enter` | The `onEnter` block threw. |
| `HandlerType.Hook.Exit` | The `onExit` block threw. |
| `HandlerType.Hook.Update` | The `onUpdate` block threw. |

---

## If `onError` itself throws

If the recovery hook throws a second exception, the runtime gives up: it notifies plugins via
`Plugin.onError` and does not attempt further recovery. The state remains unchanged. Keep
recovery hooks simple and avoid calls that could themselves fail.

---

## Plugin notification

When no `onError` block is registered (or if the block itself throws), every installed plugin
is notified via `Plugin.onError(error, currentState, handlerType)`. This is a good place for
crash reporters or structured logging:

```kotlin
class CrashReporterPlugin : Plugin<MyState, MyAction, MyEffect> {
    override fun onError(
        error: Throwable,
        currentState: MyState,
        handlerType: HandlerType<MyAction>,
    ) {
        crashReporter.recordException(error, mapOf(
            "state" to currentState::class.simpleName,
            "handler" to handlerType.toString(),
        ))
    }
}
```
