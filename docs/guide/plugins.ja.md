# プラグイン

プラグインは、単一の処理コルーチン内でマシンのイベントを同期的に監視します。各イベントの後、登録順に呼び出されます。プラグインのロジックは高速に保ってください — 重い処理はコルーチンを起動して行います。

---

## プラグインのインストール

`store { }` または `stateMachine { }` DSL ブロック内で `install(plugin)` を呼び出します:

```kotlin
val store = store<MyState, MyAction, MyEffect>(scope) {
    initialState(MyState.Idle)
    // …
    install(LoggingPlugin(tag = "MyStore"))
}
```

複数のプラグインをインストールできます。宣言順に呼び出されます。

---

## 組み込み: `LoggingPlugin`

アクション、ステート遷移、エフェクト、リジェクション、エラーのすべてをプラットフォームロガーに記録します。

```kotlin
install(LoggingPlugin(tag = "Auth"))
```

出力例:

```
[ACTION]     LoginAction.Submit
[TRANSITION] LoginState.Submitting
[EFFECT]     LoginEffect.NavigateToHome
[UNHANDLED]  LoginAction.Logout  (state: Authenticated)
[ERROR]      IllegalStateException: token expired  (handler: Hook.Enter)
```

カスタム `Logger` を渡して出力先を変更できます:

```kotlin
install(LoggingPlugin(tag = "Auth") { tag, message -> Log.d(tag, message) })
```

---

## カスタムプラグインの作成

`Plugin` インターフェースを実装し、必要なコールバックのみをオーバーライドします:

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

プラグインは同期的に実行されます。非同期処理にはコンストラクタでキャプチャした `CoroutineScope` を使用してください:

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

名前付きクラスが不要なアドホックなプラグインには、`plugin { }` ビルダーを使用します。必要なフックのみを登録します:

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

各フックは、関連するプロパティを名前で公開する型付きスコープを受け取ります:

| フック | スコープのプロパティ |
|---|---|
| `onAction { }` | `currentState`, `action` |
| `onEffect { }` | `effect` |
| `onTransition { }` | `fromState`, `toState` |
| `onUnhandled { }` | `currentState`, `action` |
| `onRejected { }` | `currentState`, `handlerType` |
| `onError { }` | `error`, `currentState`, `handlerType` |

### 型フィルター付きフック

型引数を渡すと、その型に一致するイベントのみを受け取れます。スコープの型付きプロパティはその型にキャストされるため、ブロック内で明示的なキャストは不要です:

```kotlin
install(plugin {
    onAction<LoginAction.Submit> {
        analytics.trackLogin(action.username)    // action: LoginAction.Submit
    }
    onEffect<LoginEffect.NavigateToHome> {
        navigator.navigate(effect.destination)   // effect: LoginEffect.NavigateToHome
    }
    onTransition<LoginState.Authenticated> {
        println("entered: $toState")             // toState: LoginState.Authenticated
    }
    onError<NetworkException> {
        logger.warn("network: ${error.message}") // error: NetworkException
    }
})
```

同じフックを複数回登録でき、登録順に呼び出されます:

```kotlin
install(plugin {
    onTransition<LoginState.Loading>  { analytics.track("loading") }
    onTransition<LoginState.Error>    { analytics.track("error") }
})
```

---

## `StoreRegistry` によるグローバルプラグイン

レジストリ内のすべてのストア（既存および将来のもの）にプラグインをアタッチするには `StoreRegistry.install` を使用します。ファクトリラムダは対象ストアへのアクセスを提供する `PluginScope` を受け取るため、各ストアが独自のプラグインインスタンスを取得します:

```kotlin
val registry = StoreRegistry(viewModelScope) {
    install { LoggingPlugin(tag = store.name) }
}
```

`PluginScope` が公開するプロパティ:

| プロパティ | 値 |
|---|---|
| `store` | プラグインがアタッチされる `Store` インスタンス。 |
| `name` | ストアの明示的な名前（設定されている場合）、なければクラスのシンプル名、それもなければ `id`。 |

最善の識別子を取得するには `store.name` ではなく `name` を使用します:

```kotlin
StoreRegistry(viewModelScope) {
    install { LoggingPlugin(tag = name) }   // "NewsListStore"、"AuthStore" など
}
```

`plugin { }` DSL を使ったインラインのストアごとのプラグイン:

```kotlin
StoreRegistry(viewModelScope) {
    install {
        plugin {
            onTransition { println("[${name}] $fromState → $toState") }
        }
    }
}
```

ステートレスなプラグインを全ストアで共有するには `+` を使用します:

```kotlin
StoreRegistry(viewModelScope) {
    +MyStatelessPlugin()
}
```

レジストリ経由でインストールされたプラグインは、構築時にストアに直接インストールされたプラグインの**後に**、インストール順で呼び出されます。
