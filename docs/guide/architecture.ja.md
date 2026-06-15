# アーキテクチャ

このページでは Monaka ランタイムの内部設計について説明します。

---

## 概要

```
┌─────────────────────────────────────────────────────────┐
│  DSL layer                                              │
│  store { }  ·  stateMachine { }  ·  StateMachineStore   │
└───────────────────────┬─────────────────────────────────┘
                        │ builds StateMachine (config)
┌───────────────────────▼─────────────────────────────────┐
│  Runtime layer                 DefaultStore              │
│                                                         │
│   Channel<Trigger>(UNLIMITED)                           │
│   └─ processingJob (single coroutine)                   │
│        ├─ processAction                                 │
│        │    └─ resolveActionHandler (exact → BFS)       │
│        ├─ processLifecycleEvent                         │
│        └─ processStateHook                              │
│                                                         │
│   JobRegistry  (keyed cancellable tasks)                │
│   StoreRegistry  (multi-machine coordination)           │
└─────────────────────────────────────────────────────────┘
```

---

## シングルコルーチンアクターモデル

すべての `Store` インスタンスは1つの `Channel<Trigger>(UNLIMITED)` と1つの処理コルーチン（`processingJob`）を所有します。すべての入力はトリガーとしてこのチャンネルに送られます。

処理コルーチンはトリガーを**一度に1つ**、到着順に処理します。

**結果:**
- ステート遷移は競合フリー。複数のスレッドやコルーチンから同時に `dispatch()` を呼び出しても安全です。
- `dispatch()` はサスペンドしません。チャンネルが `UNLIMITED` なので `Channel.trySend` は即座に成功します。
- 長い処理をするインラインサスペンドハンドラー（ブロッキングパターン）は、その間キューを停止させます。キューの応答性を保つには `task { }`（ファイアアンドディスパッチ）を使用してください。

---

## ハンドラー解決 — 完全一致から BFS

アクションが到着すると、ランタイムは2段階のルックアップでハンドラーを解決します:

1. **完全一致** — `actionHandlers[state::class][action::class]` が存在するか確認。
2. **祖先 BFS** — 見つからない場合、登録順に現在のステートのスーパータイプを走査。最初の一致が優先。

---

## `Store` ライフサイクル

```
Idle ──▶ Running ──▶ Canceled
```

- **Idle** — ストアは構築済みだが `start()` はまだ呼ばれていません。
- **Running** — `start()` が呼ばれた（またはコレクターが `state`/`effects`/`actions` を購読した）。初期ステートの `onEnter` が一度発火します。
- **Cancelled** — `stop()` が呼ばれた、または所有する `CoroutineScope` がキャンセルされた。
