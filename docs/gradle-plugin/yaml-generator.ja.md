# YAML ジェネレーター

YAML ジェネレーターは Kotlin ソースファイルをスキャンして `stateMachine { }` DSL ブロックを検出し、マシン1つにつき1つの `.yaml` ファイルを生成します。出力はマシンが読み取れる仕様として機能し、人間向けドキュメント、[スタブジェネレーター](stub-generator.md)へのインプット、[PlantUML ジェネレーター](puml-generator.md)へのインプットとして使用されます。

---

## セットアップ

```kotlin
monakaYamlGenerator {
    sources.setFrom(fileTree("src/commonMain/kotlin") { include("**/*.kt") })
    yamlOutputDir.set(layout.buildDirectory.dir("monaka-yaml"))
}
```

---

## 実行方法

```bash
./gradlew generateMonakaYaml
```

---

## 出力フォーマット

```yaml
name: Login
initial: Idle

states:
  Idle:
    Submit:
      transition: [Submitting]

  Submitting:
    onEnter:
      task:
        key: login
        autoCancel: true
        dispatch: [LoginSucceeded, LoginFailed]
    LoginSucceeded:
      transition: [Authenticated]
    LoginFailed:
      transition: [Idle]
      effect: [ShowError]

  Authenticated: {}
```

### トップレベルキー

| キー | 説明 |
|---|---|
| `name` | DSL から派生したマシン名。 |
| `initial` | 初期ステートのシンプル名。 |
| `states` | ステートパス → ステートノードのマップ。 |

### ステートノードキー

| キー | 説明 |
|---|---|
| `onEnter` | マシンがこのステートに入ったときに呼ばれるフック。 |
| `onExit` | マシンがこのステートを離れるときに呼ばれるフック。 |
| `onUpdate` | マシンが同じステート型に新しい値で再入するときに呼ばれるフック。 |
| `<ActionName>` | ディスパッチされたアクションのハンドラー。 |

### ハンドラー/フックボディキー

| キー | 説明 |
|---|---|
| `transition` | ターゲットステート名のインラインリスト: `[Loading]`。 |
| `effect` | エフェクト名のインラインリスト: `[ShowError, Navigate]`。 |
| `dispatch` | このハンドラーからディスパッチされるアクション名のインラインリスト。 |
| `task` | 非同期タスクディスクリプタ。 |
| `reject` | ハンドラーが `reject()` を呼ぶ場合は `true`。 |
