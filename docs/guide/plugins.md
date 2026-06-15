# Plugins

Plugins observe machine events synchronously inside the single processing coroutine. They are
called in registration order after each event. Keep plugin logic fast — launch coroutines for
any heavy work.

---

## Installing a plugin

Call `install(plugin)` inside the `store { }` or `stateMachine { }` DSL block:

```kotlin
val store = store<MyState, MyAction, MyEffect>(scope) {
    initialState(MyState.Idle)
    // …
    install(LoggingPlugin(tag = "MyStore"))
}
```

Multiple plugins can be installed; they are called in declaration order.

---

## Built-in: `LoggingPlugin`

Logs every action received, every state transition, every effect, every rejection, and every
error to the platform logger.

```kotlin
install(LoggingPlugin(tag = "Auth"))
```

Sample output:

```
[ACTION]     LoginAction.Submit
[TRANSITION] LoginState.Submitting
[EFFECT]     LoginEffect.NavigateToHome
[UNHANDLED]  LoginAction.Logout  (state: Authenticated)
[ERROR]      IllegalStateException: token expired  (handler: Hook.Enter)
```

To redirect output to a platform logger (Logcat, NSLog, SLF4J, etc.), pass a custom `Logger`:

```kotlin
install(LoggingPlugin(tag = "Auth") { tag, message -> Log.d(tag, message) })
```

---

## Exception safety

Plugin callbacks are isolated — an exception thrown inside any single plugin is caught and
discarded so it cannot crash the processing coroutine or prevent other plugins from running.
This means a misbehaving plugin fails silently. If you need to observe plugin failures (e.g.
in CI or debug builds), add explicit error handling inside your plugin:

```kotlin
class SafeAnalyticsPlugin : Plugin {
    override fun onTransition(fromState: State, toState: State) {
        runCatching {
            analytics.track(fromState, toState)
        }.onFailure { error ->
            logger.e("AnalyticsPlugin failed", error)
        }
    }
}
```

---

## Writing a custom plugin

Implement the `Plugin` interface and override only the callbacks you need:

```kotlin
class AnalyticsPlugin : Plugin {

    override fun onTransition(fromState: State, toState: State) {
        analytics.track(
            event = "state_transition",
            properties = mapOf("from" to fromState::class.simpleName, "to" to toState::class.simpleName),
        )
    }

    override fun onRejected(currentState: State, handlerType: HandlerType<Action>) {
        analytics.track("action_rejected")
    }

    override fun onError(error: Throwable, currentState: State, handlerType: HandlerType<Action>) {
        crashReporter.log(error)
    }
}
```

### Available callbacks

| Callback | When it is called |
|---|---|
| `onAction(state, action)` | Just before an action is dequeued and processed. `state` reflects the actual state at dequeue time, which may differ from the state when `dispatch()` was called. |
| `onTransition(from, to)` | A state transition was recorded and applied. |
| `onEffect(effect)` | A side effect was emitted. |
| `onUnhandled(state, action)` | No `on<>` handler was registered for the current state + action pair. |
| `onRejected(state, handlerType)` | A handler explicitly called `reject()`. |
| `onError(error, state, handlerType)` | An unhandled exception was thrown inside a handler or hook. The state is **not** changed. |

### Launching coroutines from a plugin

Plugins run synchronously; use the `CoroutineScope` you captured at construction time for any
async work:

```kotlin
class MetricsPlugin(
    private val scope: CoroutineScope,
    private val metricsClient: MetricsClient,
) : Plugin {

    override fun onTransition(fromState: State, toState: State) {
        scope.launch {
            metricsClient.record(fromState, toState)
        }
    }
}
```

---

## `plugin { }` DSL

For ad-hoc plugins that don't need a named class, use the `plugin { }` builder. Register only
the hooks you need:

```kotlin
install(plugin {
    onAction {
        println("→ $action in $currentState")
    }
    onTransition {
        println("$fromState → $toState")
    }
    onError {
        crashReporter.record(error)
    }
})
```

Each hook receives a typed scope that exposes the relevant properties by name:

| Hook | Scope properties |
|---|---|
| `onAction { }` | `currentState`, `action` |
| `onEffect { }` | `effect` |
| `onTransition { }` | `fromState`, `toState` |
| `onUnhandled { }` | `currentState`, `action` |
| `onRejected { }` | `currentState`, `handlerType` |
| `onError { }` | `error`, `currentState`, `handlerType` |

### Type-filtered hooks

Pass a type argument to receive only events matching that specific type. The scope's typed
property is cast to that type, so no explicit cast is needed inside the block:

```kotlin
install(plugin {
    onAction<LoginAction.Submit> {
        analytics.trackLogin(action.username)   // action: LoginAction.Submit
    }
    onEffect<LoginEffect.NavigateToHome> {
        navigator.navigate(effect.destination)  // effect: LoginEffect.NavigateToHome
    }
    onTransition<LoginState.Authenticated> {
        println("entered: $toState")            // toState: LoginState.Authenticated
    }
    onError<NetworkException> {
        logger.warn("network: ${error.message}") // error: NetworkException
    }
})
```

Multiple registrations of the same hook are supported and fire in registration order:

```kotlin
install(plugin {
    onTransition<LoginState.Loading>  { analytics.track("loading") }
    onTransition<LoginState.Error>    { analytics.track("error") }
})
```

---

## Global plugins via `StoreRegistry`

To attach a plugin to every store in the registry — both existing and future — use
`StoreRegistry.install`. The factory lambda receives a `PluginScope` with access to the
target store, so each store gets its own plugin instance:

```kotlin
val registry = StoreRegistry(viewModelScope) {
    install { LoggingPlugin(tag = store.name) }
}
```

`PluginScope` exposes:

| Property | Value |
|---|---|
| `store` | The `Store` instance the plugin is being attached to. |
| `name` | The store's explicit name if set, otherwise its class simple name, otherwise its `id`. |

Use `name` (not `store.name`) to get the best available identifier:

```kotlin
StoreRegistry(viewModelScope) {
    install { LoggingPlugin(tag = name) }           // "NewsListStore", "AuthStore", etc.
}
```

The `plugin { }` DSL works naturally here for inline per-store plugins:

```kotlin
StoreRegistry(viewModelScope) {
    install {
        plugin {
            onTransition { println("[${name}] $fromState → $toState") }
        }
    }
}
```

To share a single plugin instance across all stores (for stateless plugins), use `+`:

```kotlin
StoreRegistry(viewModelScope) {
    +MyStatelessPlugin()
}
```

Plugins installed via the registry fire **after** any plugins installed directly on the store
at construction time, in installation order.
