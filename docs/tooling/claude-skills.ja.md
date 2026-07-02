# Claude Code スキル

Monaka は [Claude Code](https://claude.com/claude-code) プラグインを同梱しています。これにより、AI
コーディングアシスタントが、依存関係の追加・ステートマシンの記述・テストの作成・Compose との連携・複数
マシンの連携・コード生成のセットアップまで、あなた自身のプロジェクトで Monaka を正しく使う手助けをできます。

プラグインはこのリポジトリの [`plugins/monaka`](https://github.com/wasabi-muffin/monaka/tree/main/plugins/monaka)
にあり、リポジトリ自体が Claude Code のマーケットプレイスも兼ねています。

## インストール

Claude Code を使っている任意のプロジェクトから:

```
/plugin marketplace add wasabi-muffin/monaka
/plugin install monaka@monaka
```

最初のコマンドでこのリポジトリをマーケットプレイスとして登録し、2 つ目で `monaka` プラグインをインストール
します。インストール後は、Monaka に関する作業を依頼すると Claude が該当スキルを自動的に読み込みます。スキル
を手動で呼び出す必要はありません。

!!! tip "マーケットプレイスを使わない場合"
    マーケットプレイスを使いたくない場合は、`plugins/monaka/skills/` にあるスキルフォルダーを、プロジェクトの
    `.claude/skills/` ディレクトリ（すべてのプロジェクトで使うなら `~/.claude/skills/`）にコピーしてください。
    各スキルは自己完結しています。

## スキル

| スキル | こんなときに使います |
|---|---|
| `monaka-setup` | モジュールへの Monaka の追加 — Gradle 依存関係、バージョンカタログ、ターゲット。 |
| `monaka-state-machines` | ステートマシンの記述 — `State`/`Action`/`Effect`、DSL、ハンドラー動詞、フック、階層的ステート、プラグイン。 |
| `monaka-testing` | `monaka-test` DSL によるステートマシンのテスト。 |
| `monaka-compose` | `Store` を Compose Multiplatform UI へ接続。 |
| `monaka-multi-machine` | `StoreRegistry` と `relay` による複数ストアの連携。 |
| `monaka-codegen` | `@Transition` KSP プロセッサー、または Gradle の YAML/PlantUML/スタブジェネレーターのセットアップ。 |

## バージョン管理

スキルはライブラリに追従します。Monaka のバージョンは自分の `libs.versions.toml` に固定し、最新版は
[Maven Central のリリース](https://central.sonatype.com/artifact/dev.gmvalentino.monaka/monaka)で確認して
ください。スキルは詳細についてこのドキュメントサイトを参照します。
