# クイックスタート

ステートマシンをエンドツーエンドで動かすために必要な4つのステップを解説します。

## 1. 型を定義する

フィーチャーに対して `State`・`Action`・`Effect` を実装します。最もシンプルな形は、ステートにデータクラス、アクションとエフェクトにシールドインターフェースを使う方法です:

```kotlin
data class CounterState(val count: Int) : State

sealed interface CounterAction : Action {
    data object Increment : CounterAction
    data object Decrement : CounterAction
    data object Reset     : CounterAction
}

sealed interface CounterEffect : Effect {
    data object Saved : CounterEffect
}
```

## 2. ストアを構築する

`CoroutineScope` を渡し、DSL でステートとハンドラーを設定します:

```kotlin
val counter = store<CounterState, CounterAction, CounterEffect>(viewModelScope) {
    initialState(CounterState(0))

    state<CounterState> {
        on<CounterAction.Increment> { transition(state.copy(count = state.count + 1)) }
        on<CounterAction.Decrement> { transition(state.copy(count = state.count - 1)) }
        on<CounterAction.Reset> {
            transition(CounterState(0))
            sideEffect(CounterEffect.Saved)
        }
    }

    install(LoggingPlugin(tag = "Counter"))
}
```

`state<T>` は `T` のインスタンスであるすべてのステートに対してハンドラーブロックを登録します。複数の `state` ブロックを登録でき、最も具体的なマッチ（完全一致または BFS による最近傍のスーパータイプ）が優先されます。詳細は[階層的ステート](../guide/hierarchical-states.md)を参照してください。

## 3. 観察する

任意のコルーチンスコープから `state` と `effects` を収集します:

```kotlin
// state は StateFlow — 常に現在の値を保持
counter.state.collect { render(it) }

// effects は一度限り — 取りこぼしがないよう専用のコレクターを使用
counter.effects.collect { handle(it) }
```

Compose では、UI がバックグラウンドに移行したときに収集を停止するよう、`monaka-compose` の `toViewStore()` / `handleEffects { }` ヘルパーを使用してください。

## 4. アクションをディスパッチする

```kotlin
counter.dispatch(CounterAction.Increment)
counter.dispatch(CounterAction.Reset)
```

アクションは `UNLIMITED` チャンネルにエンキューされ、シングルコルーチンによって一度に1つずつ処理されます。`dispatch` はどのスレッドやコルーチンからも安全に呼び出せます。

---

## Android — ViewModel

```kotlin
class CounterViewModel : ViewModel() {
    val store = store<CounterState, CounterAction, CounterEffect>(viewModelScope) {
        initialState(CounterState(0))
        // …
    }
}
```

`viewModelScope` がクリアされると、ストアは自動的にキャンセルされます。

## Compose Multiplatform — コンポジションスコープストア

Android 以外のターゲット（またはストアを ViewModel ではなくコンポジションのライフタイムに紐付けたい場合）では、`rememberStore` を使用します:

```kotlin
@Composable
fun CounterScreen() {
    val store = rememberStore { scope ->
        CounterStateMachine(scope, counterRepository)
    }
    // コンポーザブルがコンポジションを離れるとストアはキャンセルされる
}
```
