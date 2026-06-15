# ライフサイクルフック

Monakaには2種類のライフサイクルフックがあります: マシンがステートに入ったり出たりするときに発火する**ステートライフサイクルフック**と、プラットフォームイベント（一時停止、再開など）をマシンに転送する**アプリライフサイクルフック**です。

---

## ステートライフサイクルフック

ステートライフサイクルフックは `state<T> { }` ブロック内で宣言され、正常なトランジションの**後**に発火します。

| フック | 発火タイミング |
|---|---|
| `onEnter { }` | このステート型に入ったとき。`start()` 呼び出し時に**初期ステート**に対して一度発火し、その後は異なる型からこの型へのすべてのトランジションで発火します。 |
| `onExit { }` | このステート型から離れようとするとき。 |
| `onUpdate { }` | ステート型は同じだが値が変化したとき（データクラスのフィールド更新など）。 |

型変化時の発火順序: **`onExit`**（前のステート）→ **`onEnter`**（次のステート）。

```kotlin
state<MyState.Loading> {
    onEnter {
        task("poll") {
            while (true) {
                delay(5_000)
                dispatch(MyAction.Refresh)
            }
        }
    }
    onExit {
        cancel("poll")
    }
}

state<MyState.Active> {
    onUpdate {
        if (fromState.query != state.query) {
            task { analytics.track(state.query) }
        }
    }
}
```

---

## アプリライフサイクルフック

Android / iOS のライフサイクルイベントをマシンに転送するには、ストアの `onLifecycleEvent` を呼び出します:

```kotlin
store.onLifecycleEvent(LifecycleEvent.OnResume)
store.onLifecycleEvent(LifecycleEvent.OnPause)
```

DSL でそれらのイベントに反応します:

```kotlin
state<TimerState.Running> {
    onPause  { dispatch(TimerAction.Pause)  }
    onResume { dispatch(TimerAction.Resume) }
}
```

| `LifecycleEvent` | フックメソッド |
|---|---|
| `OnCreate` | `onCreate { }` |
| `OnStart` | `onStart { }` |
| `OnResume` | `onResume { }` |
| `OnPause` | `onPause { }` |
| `OnStop` | `onStop { }` |
| `OnDestroy` | `onDestroy { }` |

### Compose Multiplatform

`bindLifecycle()` 拡張（`monaka-compose` から）は `androidx.lifecycle.Lifecycle.Event` を `LifecycleEvent` にブリッジし、`expect`/`actual` 不要でイベントを自動転送します:

```kotlin
@Composable
fun MyScreen(store: Store<MyState, MyAction, MyEffect>) {
    store.bindLifecycle()
    // …
}
```
