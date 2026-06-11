# タイマー

**ソース:** `sample/shared/…/examples/timer/`

タイマーは一時停止/再開をサポートするカウントダウンをモデル化し、`autoCancel` 付きキー付きタスク・ライフサイクルフック（`onPause`/`onResume`）・共有プロパティ更新のための `@SelfTransition`/`toSelf()` を示します。

---

## デモされるパターン

### `autoCancel` 付きキー付きタスク

ティックループは `key = "tick"` と `autoCancel = true` で `Running.onEnter` に起動されます。ステート型が変化すると自動的にキャンセルされます。

### ユーザー一時停止とライフサイクル一時停止の分離

専用の `PauseForLifecycle` アクションと `Paused` の `pausedByLifecycle` フラグを使って区別します。ライフサイクル起因の一時停止のみが `ON_RESUME` で自動再開します。

### 共有プロパティ更新のための `toSelf()`

`SetAutoPause` はすべてのステートで有効で、共有フィールド `autoPause` のみを変更します。各ステート型ごとに `copy(autoPause = …)` を書く代わりに `toSelf()` を使います:

```kotlin
on<TimerAction.SetAutoPause> {
    transition(state.toSelf(autoPause = action.enabled))
}
```

### オプトインのライフサイクルインテグレーション

`autoPause` フラグはすべてのステートを通じて保持されます。スクリーンはフラグ値に関係なく `onLifecycleEvent` を転送し、マシンが `state.autoPause` に基づいて動作を決定します。
