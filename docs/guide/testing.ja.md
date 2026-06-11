# テスト

`monaka-test` モジュールは、ステートマシンを独立してテストするためのコルーチン対応 DSL を提供します。
[Turbine](https://github.com/cashapp/turbine) と `kotlinx-coroutines-test` をラップしているため、テストは実際の遅延なしに仮想時間で実行されます。

---

## セットアップ

```kotlin
commonTest.dependencies {
    implementation("dev.gmvalentino.monaka:monaka-test:<version>")
    implementation(kotlin("test"))
}
```

---

## エントリーポイント — `testStore`

```kotlin
@Test
fun counterIncrements() = testStore(machine = counterMachine) {
    testCase("increment once") {
        trigger(CounterAction.Increment) {
            expectState<CounterState> { state.count == 1 }
        }
    }
}
```

各 `testCase` ブロックは同じ `StateMachine` 設定から構築された**フレッシュなストア**を受け取ります。

---

## DSL リファレンス

### `given(state)`

マシンの初期ステートを上書きします。最初の `trigger` の前に呼び出す必要があります。

### `trigger(action) { … }`

アクションをディスパッチし、ブロック内で結果の発行をアサートします。

### `trigger(LifecycleEvent) { … }`

ライフサイクルイベントを転送し、発行をアサートします。

### `trigger(StateHook) { … }`

ステートライフサイクルフックを手動で発火します — フル遷移なしで `onEnter` / `onExit` / `onUpdate` をテストするのに有用です。

### `advanceTime(duration) { … }`

仮想時間を進め、時間付き処理が生成する発行をアサートします。

---

## アサーションメソッド

### ステート

| メソッド | 動作 |
|---|---|
| `expectState(state)` | 次のステートが `state` と等しいことをアサート。 |
| `expectState<T> { predicate }` | 次のステートが `T` のインスタンスでオプショナルな述語に一致することをアサート。 |
| `skipState()` | アサートなしで次のステートを消費。 |

### エフェクト

| メソッド | 動作 |
|---|---|
| `expectEffect(effect)` | 次のエフェクトが `effect` と等しいことをアサート。 |
| `expectEffect<T> { predicate }` | 次のエフェクトが `T` のインスタンスで述語に一致することをアサート。 |
| `expectNoEffects()` | エフェクトが発行されていないことをアサート。 |
| `skipEffect()` | アサートなしで次のエフェクトを消費。 |

### ハンドラー起点のアクション

| メソッド | 動作 |
|---|---|
| `expectAction(action)` | 次のハンドラーアクションが `action` と等しいことをアサート。 |
| `expectAction<T> { predicate }` | 次のハンドラーアクションが `T` のインスタンスで述語に一致することをアサート。 |
| `expectNoAction()` | ハンドラー起点のアクションが発行されていないことをアサート。 |
| `skipAction()` | アサートなしで次のハンドラーアクションを消費。 |

---

## 自動アイドルチェック

すべてのテストケース終了時に `testStore` は自動的に `expectIdle()` を呼び出し、3つのストリームがすべて消費されたことをアサートします。

```kotlin
testCase("fires and forgets", exhaustive = false) { … }
```
