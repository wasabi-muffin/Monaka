# FAQ & トラブルシューティング

---

## 初期ステートの `onEnter` が発火しない

`onEnter` はストアが最初に構築されたときの初期ステートに対しては呼ばれません。コレクターが `state`・`actions`・`effects` のいずれかを購読した最初のタイミングで発火します。

コレクターがアタッチする前に発火させる必要がある場合は、`store.start()` を明示的に呼んでください:

```kotlin
val store = store<MyState, MyAction, MyEffect>(scope) { … }
store.start()
```

---

## 遅れたサブスクライバーがエフェクトを受け取れない

エフェクトは `replay = 0` の `SharedFlow` で発行されます。**Compose** では `monaka-compose` の `handleEffects { }` を使用してください。

---

## スタブジェネレーターが既存ファイルをスキップする

デフォルトでは `generateMonakaStubs` は既存ファイルを上書きしません。上書きするには:

```bash
./gradlew generateMonakaStubs --replace=true
```

---

## ステートマシンハンドラーが呼ばれない

以下の順序で確認してください:

1. 現在のステートに `state<T>` ブロックが登録されているか？
2. アクション型が正しい `state<T>` ブロック内に登録されているか？
3. 親ブロックがすでにアクションを処理していないか？
4. 前のハンドラーが `reject()` を呼んでいないか？

`LoggingPlugin` を有効にしてランタイムの動作をトレースしてください。

---

## ハンドラー内の `dispatch()` がすぐに処理されない

`dispatch()` はアクションをチャンネルの末尾にエンキューします。現在のハンドラーが終了した**後**に処理されます。これは設計上の動作です。

---

## 複数の `transition()` 呼び出し — 最初のみ有効

`transition()` は最初の書き込みが優先です。2度目の呼び出しは無視されます。排他的な分岐が必要な場合は `if/else` を使用してください。

---

## `onUpdate` が予期せず発火する

`onUpdate` はステート**型**が同じで**値**が変化したときに発火します — 気にしないフィールドの更新も含みます。`fromState`/`toState` アクセサーを使ってガードしてください。

---

## `StoreRegistry` が重複登録でスローする

同じストアインスタンス（同じ `id`）を2度登録すると `IllegalArgumentException` がスローされます。`register` を使ってください — 所有スコープがキャンセルされたときにストアを自動的に登録解除します。

---

## `testStore` が "no initial state" で失敗する

`testStore` は `initialState(…)` が設定された `stateMachine { }` を必要とします。各テストケースで特定の開始ポイントが必要な場合は `given(state)` を使用してください。
