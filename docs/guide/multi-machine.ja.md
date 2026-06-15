# マルチマシンコーディネーション

複数の独立したステートマシンにまたがる機能がある場合、`StoreRegistry` と `relay` を使うことで、マシン同士が互いに参照を保持しなくても宣言的に連携させることができます。

---

## StoreRegistry

```kotlin
val registry = StoreRegistry(bridgeScope = viewModelScope)
```

`StoreRegistry` は次の3つの役割を持ちます:

1. **キーイング** — ストアを `KClass` で管理し、直接参照なしにディスパッチできます。
2. **リレー** — `bind(…)` でインストールされたリレーは、一致するストアが登録された時点で監視を開始します。
3. **グローバルプラグイン** — `install { … }` でインストールされたプラグインは、現在のすべてのストアと将来登録されるすべてのストアにアタッチされます。

### グローバルプラグイン

`install` を使って、すべてのストア（後から登録されるものも含む）にプラグインをアタッチします:

```kotlin
val registry = StoreRegistry(viewModelScope) {
    install { LoggingPlugin(tag = name) }   // name = 最善の識別子
}
```

`PluginScope.name` は、ストアの明示的な名前、クラスのシンプル名、`id` の順でフォールバックします。詳細は [プラグイン — StoreRegistry によるグローバルプラグイン](plugins.ja.md#storeregistry-によるグローバルプラグイン) を参照してください。

**スレッドセーフティ:** `StoreRegistry` はスレッドセーフではありません。`register`・`unregister`・`bind`・`install`・`get`/`getAll` へのすべての呼び出しは同じスレッドから行う必要があります。

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

## リレーハンドラーのスキップ

ターゲットストアクラスのインスタンスが1つも登録されていない場合、リレーはそのイベントに対してハンドラーを**スキップ**します。コレクターコルーチン自体は動作し続け、宣言されたターゲットクラスのいずれかが再登録されると、次のイベントからハンドラーが再び実行されます。

ターゲットクラスの追跡は自動で行われるため、追加の設定は不要です。リレーが初めて実行されて `dispatch(TargetStore::class, …)` を呼び出すと、`TargetStore::class` がリレーの内部ターゲットセットに追加されます。それ以降、各イベントで `targets.any { it in registry }` を確認し、登録済みのターゲットがなければハンドラーの処理全体がスキップされます。

`Relay.targets` をオーバーライドしないカスタム `Relay` 実装は従来の動作を維持します: ターゲットの登録状況に関わらず、常にハンドラーが実行されます。

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
