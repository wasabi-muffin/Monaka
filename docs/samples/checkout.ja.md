# チェックアウト（マルチマシン）

**ソース:** `sample/shared/…/examples/checkout/`

チェックアウトサンプルは、Auth・Cart・Checkoutの3つの独立したステートマシンにまたがるeコマースフローをモデル化します。マシンはお互いの参照を持たず、すべての結合は `StoreRegistry` と `Relay` を使った単一の `AppCoordinator` にあります。

---

## マシン

| マシン | ステート | 責務 |
|---|---|---|
| `AuthStateMachine` | `SignedOut`・`SigningIn`・`SignedIn` | サインイン/サインアウト |
| `CartStateMachine` | `Empty`・`Loading`・`Loaded`・`Error` | ショッピングカートの内容 |
| `CheckoutStateMachine` | `Idle`・`Summary`・`Processing`・`Done`・`Failed` | 支払いフロー |

---

## リレートポロジー

```
AuthStore  state(SignedIn)      ──▶ CartStore.LoadForUser(event.user.id)
AuthStore  state(SignedOut)     ──▶ CartStore.Clear
AuthStore  state(SignedOut)     ──▶ CheckoutStore.Cancel

CartStore  effect(CartChanged)  ──▶ CheckoutStore.SyncCart(event.items, event.total)

CheckoutStore  state(Done)      ──▶ CartStore.Clear
```

---

## デモされるパターン

### リレーによる関心の分離

各マシンは自分のドメインのみを処理します。リレーオブジェクトの宣言が単一の読みやすいトポロジードキュメントを形成します。

### ステートリレーとエフェクトリレー

`AuthRelay` は**ステート**に反応します — マシンがそのステートに入るたびに発火します。`CartRelay` は**エフェクト**に反応します — その一度限りのエフェクトが発行されたときのみ発火します。

### `register` と自動クリーンアップ

`register` は所有する `CoroutineScope` がキャンセルされたときにストアを停止・登録解除する `invokeOnCompletion` コールバックをアタッチします。
