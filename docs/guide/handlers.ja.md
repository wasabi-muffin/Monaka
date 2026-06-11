# ハンドラー

すべての `on<ActionType>` ラムダは `ActionScope` を暗黙のレシーバーとして受け取り、`state`（登録されたサブタイプにキャストされた現在のステート）、`action`（ディスパッチされたアクション）、およびランタイムが何をすべきかを記録する動詞メソッドへのアクセスを提供します。

ハンドラーは式ではなく**ステートメント**です — ラムダは `Unit` を返します。ランタイムはラムダが返った後に記録された結果をスナップショットします。

---

## ハンドラーパターン

### インラインサスペンド（ブロッキング）

ハンドラーのコルーチンは処理が完了するまでサスペンドします。その間、アクションキューは一時停止され、リクエストは一度に1件ずつ処理されます。

```kotlin
on<LoginAction.Submit> {
    val result = loginRepository.login(state.username, state.password)
    when (result) {
        is Success -> transition(LoginState.Authenticated(result.user))
        is Failure -> transition(LoginState.Error(result.message))
    }
}
```

### ファイアアンドディスパッチ（ノンブロッキング）

即座に返り、兄弟コルーチンで処理を実行し、完了時にフォローアップアクションをディスパッチします。

```kotlin
on<LoginAction.Submit> {
    task("login") {
        val r = loginRepository.login(state.username, state.password)
        dispatch(
            if (r is Success) LoginAction.LoginSucceeded(r.user)
            else LoginAction.LoginFailed(r.message)
        )
    }
    transition(LoginState.Submitting)
}
```

### キー付きジョブ（デバウンス / キャンセル）

`task` に文字列キーを渡すと、新しいジョブを起動する前に同じキーで実行中のジョブをキャンセルします。

```kotlin
on<SearchAction.QueryChanged> {
    task("search") {
        delay(300)
        dispatch(SearchAction.ResultsReceived(repository.search(action.query)))
    }
    transition(state.copy(query = action.query, isLoading = true))
}

on<SearchAction.Clear> {
    cancel("search")
    transition(SearchState.Idle)
}
```

---

## ハンドラー動詞

| 動詞 | 動作 |
|---|---|
| `transition(newState)` | 次のステートを記録。**最初の呼び出しが優先** — 同じハンドラー内での後続の呼び出しは無視されます。 |
| `sideEffect(e1, e2, …)` | エフェクトを追記。呼び出し順に発行されます。`transition()` が記録されない場合、ステートは変化しません（エフェクトのみのハンドラー）。 |
| `reject()` | アクションを拒否としてマーク。**終端** — 以降のすべての動詞呼び出しが無効化され、**この呼び出し前に蓄積されたエフェクトも破棄されます**。プラグインは `onRejected` で通知されます。 |
| `guard { predicate }` | `predicate` が `false` を返した場合、以降のすべての動詞を短絡。`reject()` と異なり、ガード前の記録は**保持**され、プラグインへの通知もありません。 |
| `dispatch(action)` | 後で処理するためにアクションをストアのチャンネルにエンキュー。 |
| `task { }` / `task("key") { }` | ファイアアンドフォーゲットのコルーチンを起動。オプションでキャンセル用のキーを指定可能。 |
| `cancel("key")` | 指定したキーで実行中のジョブをキャンセル。実行中のジョブがなければ無操作。 |

### `transition` は最初の書き込みが優先

```kotlin
on<Refresh> {
    if (state.isStale) transition(Refreshing)
    transition(Active)   // 上で呼び出し済みなら無視される
}
```

### 終端の `reject`

```kotlin
on<Submit> {
    if (!state.isValid) { reject(); return@on }
    transition(Submitting)
    sideEffect(Analytics.Started)
}
```

### エフェクトのみのハンドラー

```kotlin
on<CartAction.Ping> {
    sideEffect(CartEffect.Toast("Cart is ready"))
}
```

### `guard` — 条件付き短絡

```kotlin
on<MyAction.Submit> {
    sideEffect(MyEffect.Analytics)   // 常に発行される — ガード前に記録済み
    guard { state.isValid }          // 無効時は以降を短絡
    transition(MyState.Submitting)
    sideEffect(MyEffect.HideKeyboard)
}
```

| | ガード前の動詞 | プラグイン通知 |
|---|---|---|
| `guard { false }` | **保持** | なし |
| `reject()` | 破棄 | あり（`onRejected`） |
