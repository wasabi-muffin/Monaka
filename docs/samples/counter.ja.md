# Counter

**ソース:** `sample/shared/…/examples/counter/`

カウンターは最もシンプルなサンプルです。同期遷移とインラインエフェクト・ファイアアンドディスパッチ非同期パターンを組み合わせたシングルステートマシンです。ステート階層を持たないマシンとして、単一の `data class` が有効であることを示しています。

---

## 型

```kotlin
data class CounterState(val count: Int, val step: Int = 1) : State

sealed interface CounterAction : Action {
    data object Increment : CounterAction
    data object Decrement : CounterAction
    data class  SetStep(val step: Int) : CounterAction
    data object Reset : CounterAction
    data class  SaveCompleted(val success: Boolean) : CounterAction
}

sealed interface CounterEffect : Effect {
    data class ShowMessage(val text: String) : CounterEffect
    data class SaveCount(val count: Int)     : CounterEffect
}
```

---

## ステートマシン

```kotlin
class CounterStateMachine(scope: CoroutineScope) :
    Store<CounterState, CounterAction, CounterEffect> by store(scope = scope, builder = {

    initialState(CounterState(count = 0, step = 1))

    state<CounterState> {
        on<CounterAction.Increment> {
            transition(state.copy(count = state.count + state.step))
        }
        on<CounterAction.Decrement> {
            transition(state.copy(count = state.count - state.step))
        }
        on<CounterAction.SetStep> {
            if (action.step < 1) {
                sideEffect(CounterEffect.ShowMessage("Step must be at least 1."))
            } else {
                transition(state.copy(step = action.step))
            }
        }
        on<CounterAction.Reset> {
            transition(state.copy(count = 0))
            sideEffect(CounterEffect.ShowMessage("Counter reset!"))
            sideEffect(CounterEffect.SaveCount(0))
        }
        on<CounterAction.SaveCompleted> {
            if (action.success) sideEffect(CounterEffect.ShowMessage("Saved ✓"))
        }
    }

    install(LoggingPlugin(tag = "Counter"))
})
```

---

## デモされるパターン

### シングルステートマシン

状態全体が1つの `data class` に収まります。1つの `state<CounterState>` ブロックのみ — 階層もキャッチオールもありません。

### 遷移なしの条件付きサイドエフェクト

`SetStep` は入力を検証し、無効ならエフェクトを発行し、有効なら遷移します。

### 1つのハンドラーから複数のエフェクト

`Reset` は遷移**と**2つのエフェクトを記録します。エフェクトはステート変化後に呼び出し順に発行されます。

### アクション経由の往復非同期保存

`SaveCount` エフェクトをコンシューマー（ViewModel またはコンポーザブル）が処理し、非同期保存を開始します。保存完了時にコンシューマーは `SaveCompleted` をストアにディスパッチします。
