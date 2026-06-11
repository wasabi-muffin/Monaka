# プラグイン

プラグインは、単一の処理コルーチン内でマシンのイベントを同期的に監視します。各イベントの後、登録順に呼び出されます。プラグインのロジックは高速に保ってください — 重い処理はコルーチンを起動して行います。

---

## プラグインのインストール

```kotlin
val store = store<MyState, MyAction, MyEffect>(scope) {
    initialState(MyState.Idle)
    install(LoggingPlugin(tag = "MyStore"))
}
```

---

## 組み込み: `LoggingPlugin`

```kotlin
install(LoggingPlugin(tag = "Auth"))
```

出力例:

```
[Auth] → ACTION   : LoginAction.Submit
[Auth]   IN STATE : LoginState.Typing(username=alice, password=secret)
[Auth] ← STATE   : LoginState.Typing → LoginState.Submitting
[Auth]   EFFECT  : LoginEffect.NavigateToHome
[Auth] ⚠ UNHANDLED: Action(Logout)  (state: Authenticated)
[Auth] ✗ ERROR    : IllegalStateException: token expired  (handler: Hook.Enter)
```

カスタム `Logger` を渡して出力先を変更できます:

```kotlin
install(LoggingPlugin(tag = "Auth") { tag, message -> Log.d(tag, message) })
```

---

## カスタムプラグインの作成

```kotlin
class AnalyticsPlugin : Plugin<MyState, MyAction, MyEffect> {

    override fun onTransition(fromState: MyState, toState: MyState) {
        analytics.track(
            event = "state_transition",
            properties = mapOf("from" to fromState::class.simpleName, "to" to toState::class.simpleName),
        )
    }

    override fun onRejected(currentState: MyState, handlerType: HandlerType<MyAction>) {
        analytics.track("action_rejected")
    }

    override fun onError(error: Throwable, currentState: MyState, handlerType: HandlerType<MyAction>) {
        crashReporter.log(error)
    }
}
```

### 使用可能なコールバック

| コールバック | 呼び出しタイミング |
|---|---|
| `onAction(state, action)` | アクションがデキューされ処理される直前。 |
| `onTransition(from, to)` | ステート遷移が記録・適用されたとき。 |
| `onEffect(effect)` | サイドエフェクトが発行されたとき。 |
| `onUnhandled(state, action)` | 現在のステート+アクションのペアに `on<>` ハンドラーが登録されていないとき。 |
| `onRejected(state, handlerType)` | ハンドラーが明示的に `reject()` を呼び出したとき。 |
| `onError(error, state, handlerType)` | ハンドラーまたはフック内で未処理の例外がスローされたとき。ステートは変化しません。 |

### プラグインからコルーチンを起動

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
