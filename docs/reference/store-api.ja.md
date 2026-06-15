# Store API リファレンス

`Store<State, Action, Effect>` は、実行中のすべてのステートマシンインスタンスに対するパブリックコントラクトです。

---

## プロパティ

### `id: String`

このストアインスタンスの一意識別子。デフォルトで UUID として自動生成されます。

### `name: String`

ストアの人間が読める名前。ビルダー DSL の `name(…)` 呼び出しで設定します:

```kotlin
val store = store<MyState, MyAction, MyEffect>(scope) {
    name("Login")
    initialState(MyState.Idle)
}
```

`StateMachineStore` のサブクラスでは、明示的に設定されていない場合、クラスのシンプル名（例: `"LoginStateMachine"`）がデフォルトになります。インラインの `store { }` 呼び出しでは、設定しない限り空文字列になります。

`name` は `StoreRegistry.PluginScope` で公開され、ストアごとのプラグインタグ付けを容易にします:

```kotlin
StoreRegistry(viewModelScope) {
    install { LoggingPlugin(tag = name) }
}
```

### `state: StateFlow<State>`

現在のステート。`StateFlow` として公開されます。常に値を保持します。

```kotlin
store.state.collect { state -> render(state) }
```

`state` を収集すると `start()` も暗黙的に呼ばれます。

### `effects: SharedFlow<Effect>`

一度限りのサイドエフェクト。`replay = 0` の `SharedFlow` として公開されます。遅れて購読したサブスクライバーは、購読前に発行されたエフェクトを受け取れません。

**購読順序の注意:** `state` を収集すると `start()` が暗黙的に呼ばれ、初期ステートの `onEnter` フックが発火します。この時点で `effects` にサブスクライバーがいない場合、そのフックが発行したエフェクトは失われます。必ず `state` より**先に** `effects` を購読するか、すべてのサブスクライバーをアタッチした後で明示的に `start()` を呼んでください:

```kotlin
// 安全 — effects サブスクライバーの準備が整ってから state 収集が start() をトリガーする
launch { store.effects.collect { handle(it) } }
launch { store.state.collect { render(it) } }
```

Compose では `monaka-compose` の `handleEffects { }` を使うと購読順序が自動的に管理されます。

**バックプレッシャー:** エフェクトを消費するサブスクライバーがなく内部バッファが満杯になると、処理コルーチンがサスペンドしすべてのステート遷移が停止します。サブスクライバーがエフェクトを速やかに消費することを確認してください。

### `actions: SharedFlow<Action>`

ストアにディスパッチされたすべてのアクション。`replay = 0`。

### `isActive: Boolean`

ストアがアクションを処理中のとき `true`。`stop()` 後または所有スコープがキャンセルされた後は `false`。`isActive` が `false` の場合、`dispatch` や `onLifecycleEvent` などの書き込み操作はすべてサイレントなノーオペレーションになります。

注: `isActive` は `Store` インターフェースの抽象プロパティです。カスタム `Store` 実装では具体的なオーバーライドを提供する必要があり、提供しない場合はコンパイルエラーになります。

---

## 関数

### `dispatch(action: Action)`

処理のためにアクションをエンキュー。サスペンドせず、任意のスレッドやコルーチンから安全に呼び出せます。

### `start()`

初期ステートの `onEnter` フックを発火します（登録されていれば）。`state`・`actions`・`effects` のいずれかにコレクターが初めてアタッチしたとき自動的に呼ばれます。複数回呼んでも安全なノーオペレーションです。

### `stop()`

ストアを永続的に停止します。内部処理コルーチンとすべての実行中キー付きジョブをキャンセルします。

**重要:** `stop()` は所有 `CoroutineScope` をキャンセルしないため、`invokeOnCompletion` で登録されたコールバックは `stop()` を直接呼んだときには発火しません。これらのコールバックはスコープのジョブにアタッチされており、スコープがキャンセルされたときのみ発火します。早期に `stop()` を呼んで、かつストアが `StoreRegistry` に登録されている場合は、`registry.unregister(store)` を手動で呼び出してください。

### `onLifecycleEvent(event: LifecycleEvent)`

アプリライフサイクルイベントをマシンに転送します。

### `install(plugin: Plugin)`

構築後にプラグインをストアにアタッチします。プラグインは次に処理されるアクション以降からイベントを受け取り始めます。

主に `StoreRegistry` がグローバルプラグインを既存のストアに遡って適用するために使用されます。

### `invokeOnCompletion(handler: (Throwable?) -> Unit): DisposableHandle`

ストアの所有 `CoroutineScope` がキャンセルされたときに発火するコールバックを登録します。ハンドラーはキャンセル原因を受け取ります（正常完了の場合は `null`）。

**重要:** このコールバックは**スコープのキャンセル時のみ**発火します — `stop()` を直接呼んだときには発火しません。`stop()` は内部処理コルーチンをキャンセルしますが、所有スコープはそのまま残ります。両方のケースでクリーンアップが必要な場合は、`stop()` の代わりにスコープをキャンセルするか、`stop()` 後に手動でアンregisterしてください。
