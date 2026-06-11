# Store API リファレンス

`Store<State, Action, Effect>` は、実行中のすべてのステートマシンインスタンスに対するパブリックコントラクトです。

---

## プロパティ

### `id: String`

このストアインスタンスの一意識別子。デフォルトで UUID として自動生成されます。

### `state: StateFlow<State>`

現在のステート。`StateFlow` として公開されます。常に値を保持します。

```kotlin
store.state.collect { state -> render(state) }
```

`state` を収集すると `start()` も暗黙的に呼ばれます。

### `effects: SharedFlow<Effect>`

一度限りのサイドエフェクト。`replay = 0` の `SharedFlow` として公開されます。

### `actions: SharedFlow<Action>`

ストアにディスパッチされたすべてのアクション。`replay = 0`。

### `isActive: Boolean`

ストアがアクションを処理中のとき `true`。`stop()` 後または所有スコープがキャンセルされた後は `false`。

---

## 関数

### `dispatch(action: Action)`

処理のためにアクションをエンキュー。サスペンドせず、任意のスレッドやコルーチンから安全に呼び出せます。

### `start()`

初期ステートの `onEnter` フックを発火します（登録されていれば）。`state`・`actions`・`effects` のいずれかにコレクターが初めてアタッチしたとき自動的に呼ばれます。複数回呼んでも安全なノーオペレーションです。

### `stop()`

ストアを永続的に停止します。内部処理コルーチンとすべての実行中キー付きジョブをキャンセルします。

### `onLifecycleEvent(event: LifecycleEvent)`

アプリライフサイクルイベントをマシンに転送します。

### `invokeOnCompletion(handler: (Throwable?) -> Unit): DisposableHandle`

ストアの所有 `CoroutineScope` がキャンセルされたときに発火するコールバックを登録します。
