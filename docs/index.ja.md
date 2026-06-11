# Monaka

Kotlin Multiplatform MVI ステートマシンライブラリです。状態・アクション・エフェクトとその遷移を型安全な DSL で定義できます。シングルコルーチンのアクターモデルにより、確定的かつ競合のない状態更新を保証します。

**対応ターゲット:** Android · iOS (arm64、x64、Simulator arm64) · JVM · macOS · watchOS · tvOS · Linux · Windows · JS · Wasm

---

## コアコンセプト

| コンセプト | インターフェース | 役割 |
|---|---|---|
| **State** | `State` | ある時点でのマシンのスナップショット |
| **Action** | `Action` | UI またはシステムからディスパッチされる意図 |
| **Effect** | `Effect` | 一度限りのサイドエフェクト（ナビゲーション、トースト、分析） |
| **Store** | `Store<S,A,E>` | 実行中のインスタンス。`state`・`actions`・`effects`・`dispatch` を公開 |

エフェクトは一度限りです。リプレイなしの `SharedFlow` で発行されるため、遅れてサブスクライブしたコレクターは過去の発行を受け取れません。

---

## はじめに

Monaka が初めての方はこちらから:

- [インストール](getting-started/installation.md) — Gradle ビルドへのライブラリ追加
- [クイックスタート](getting-started/quickstart.md) — 型定義、ストア構築、状態観察、アクションのディスパッチ

---

## ガイド

- [ハンドラー](guide/handlers.md) — ハンドラーパターン、使用可能なすべての動詞とその意味
- [ライフサイクルフック](guide/lifecycle-hooks.md) — `onEnter`・`onExit`・`onUpdate` とアプリライフサイクル連携
- [階層的ステート](guide/hierarchical-states.md) — キャッチオール親ステートブロックとディスパッチ優先度
- [プラグイン](guide/plugins.md) — 組み込み `LoggingPlugin` とカスタムプラグインの作成

---

## Gradle プラグイン

`dev.gmvalentino.monaka` Gradle プラグインは3つのコード生成タスクを提供します:

| タスク | 出力 | ドキュメント |
|---|---|---|
| `generateMonakaYaml` | ステートマシンごとの `.yaml` スペック | [YAML ジェネレーター](gradle-plugin/yaml-generator.md) |
| `generateMonakaPuml` | マシンごとの `.puml` PlantUML ダイアグラム | [PlantUML ジェネレーター](gradle-plugin/puml-generator.md) |
| `generateMonakaStubs` | Kotlin の `State`・`Action`・`Effect`・`StateMachine` ファイル | [スタブジェネレーター](gradle-plugin/stub-generator.md) |
