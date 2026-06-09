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
[Auth] → ACTION   : LoginAction.Submit
[Auth]   IN STATE : LoginState.Typing(username=alice, password=secret)
[Auth] ← STATE   : LoginState.Typing → LoginState.Submitting
[Auth]   EFFECT  : LoginEffect.NavigateToHome
[Auth] ⚠ UNHANDLED: Action(Logout)  (state: Authenticated)
[Auth] ✗ ERROR    : IllegalStateException: token expired  (handler: Hook.Enter)
```

To redirect output to a platform logger (Logcat, NSLog, SLF4J, etc.), pass a custom `Logger`:

```kotlin
install(LoggingPlugin(tag = "Auth") { tag, message -> Log.d(tag, message) })
```

---

## Writing a custom plugin

Implement the `Plugin<S, A, E>` interface and override only the callbacks you need:

```kotlin
class AnalyticsPlugin : Plugin<MyState, MyAction, MyEffect> {

    override fun onTransition(
        fromState: MyState,
        toState: MyState,
    ) {
        analytics.track(
            event = "state_transition",
            properties = mapOf("from" to fromState::class.simpleName, "to" to toState::class.simpleName),
        )
    }

    override fun onRejected(
        currentState: MyState,
        handlerType: HandlerType<MyAction>,
    ) {
        analytics.track("action_rejected")
    }

    override fun onError(
        error: Throwable,
        currentState: MyState,
        handlerType: HandlerType<MyAction>,
    ) {
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
| `onRejected(state, handlerType)` | No handler was registered for the current state + action pair, or a handler called `reject()`. |
| `onError(error, state, handlerType)` | An unhandled exception was thrown inside a handler or hook. The state is **not** changed. |

### Launching coroutines from a plugin

Plugins run synchronously; use the `CoroutineScope` you captured at construction time for any
async work:

```kotlin
class MetricsPlugin(
    private val scope: CoroutineScope,
    private val metricsClient: MetricsClient,
) : Plugin<MyState, MyAction, MyEffect> {

    override fun onTransition(fromState: MyState, toState: MyState) {
        scope.launch {
            metricsClient.record(fromState, toState)
        }
    }
}
```
