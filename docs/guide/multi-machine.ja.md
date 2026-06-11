# マルチマシンコーディネーション

複数の独立したステートマシンにまたがる機能がある場合、`StoreRegistry` と `relay` を使うことで、マシン同士が互いに参照を保持しなくても宣言的に連携させることができます。

---

## StoreRegistry

```kotlin
val registry = StoreRegistry(bridgeScope = viewModelScope)
```

**スレッドセーフティ:** `StoreRegistry` はスレッドセーフではありません。`register`・`unregister`・`bind`・`get`/`getAll` へのすべての呼び出しは同じスレッドから行う必要があります。

---

## relay { }

```kotlin
val authRelay = relay(from = AuthStore::class) {
    state<AuthState.SignedIn> {
        dispatch(CartStore::class, CartAction.LoadForUser(event.user.id))
    }
    state<AuthState.SignedOut> {
        dispatch(CartStore::class, CartAction.Clear)
        dispatch(CheckoutStore::class, CheckoutAction.Cancel)
    }
}
```

### リレートリガー

| ブロック | トリガー条件 |
|---|---|
| `state<S> { }` | 現在のステートが `S` のインスタンスであるすべてのステート発行時。 |
| `effect<E> { }` | ソースストアから `E` のインスタンスであるサイドエフェクトが発行されるたびに。 |
| `action<A> { }` | ソースストアに `A` のインスタンスであるアクションがディスパッチされるたびに。 |

---

## ストアの登録

```kotlin
AuthStore(authMachine, viewModelScope).register(registry)
CartStore(cartMachine, viewModelScope).register(registry)
```

---

## フルサンプル — eコマースチェックアウト

```kotlin
object AuthRelay : Relay<AuthState, AuthAction, AuthEffect> by relay(from = AuthStore::class, builder = {
    state<AuthState.SignedIn> {
        dispatch(CartStore::class, CartAction.LoadForUser(event.user.id))
    }
    state<AuthState.SignedOut> {
        dispatch(CartStore::class, CartAction.Clear)
        dispatch(CheckoutStore::class, CheckoutAction.Cancel)
    }
})

object CartRelay : Relay<CartState, CartAction, CartEffect> by relay(from = CartStore::class, builder = {
    effect<CartEffect.CartChanged> {
        dispatch(CheckoutStore::class, CheckoutAction.SyncCart(event.items, event.total))
    }
})
```
