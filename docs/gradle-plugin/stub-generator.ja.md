# スタブジェネレーター

スタブジェネレーターは `.yaml` ファイルを読み込み、マシンごとに4つのKotlinソースファイルを出力します。

| ファイル | 内容 |
|---|---|
| `{Name}State.kt` | すべてのステートのシールドインターフェース階層。 |
| `{Name}Action.kt` | すべてのアクション型をリストするシールドインターフェース。 |
| `{Name}Effect.kt` | すべてのエフェクト型をリストするシールドインターフェース。 |
| `{Name}StateMachine.kt` | 生成された型に接続された `stateMachine { }` DSL。 |

これはスキャフォールディングステップです。生成されたファイルは自由に編集できます。

---

## セットアップ

```kotlin
monakaStubGenerator {
    input.set("${layout.buildDirectory.get()}/monaka-yaml")
    outputDir.set(layout.projectDirectory.dir("src/commonMain/kotlin/com/example/auth"))
    style.set(com.example.gradle.StubStyle.CLASS)
    replace.set(false)
    useTransitionAnnotation.set(true)
}
```

---

## 実行方法

```bash
./gradlew generateMonakaStubs
```

### CLI オプション

| オプション | デフォルト | 説明 |
|---|---|---|
| `--input` | 拡張機能の `input` | `.yaml` ファイルまたはディレクトリへのパス。 |
| `--output` | 拡張機能の `outputDir` または YAML ファイルのディレクトリ | 生成された `.kt` ファイルを書き込むディレクトリ。 |
| `--style` | `class` | 生成スタイル: `class` または `factory`。 |
| `--replace` | `false` | `true` のとき、既存ファイルを上書き。 |
| `--use-transition-annotation` | `true` | `@SelfTransition` / `@Transition` をステートシールドインターフェースに出力。 |

---

## 生成スタイル

### `CLASS`（デフォルト）

依存関係をコンストラクターパラメーターとして注入する場合に使用します。

### `FACTORY`

外部依存関係のないマシン、またはクロージャキャプチャーアプローチを好む場合に使用します。

---

## 典型的なワークフロー

```
1. Monaka DSL を使ってステートマシンを作成。
2. ./gradlew generateMonakaYaml    → build/monaka-yaml/Login.yaml を生成
3. ./gradlew generateMonakaStubs   → LoginState.kt, LoginAction.kt, … を生成
4. 生成ファイルをソースツリーに移動/コピー。
5. 生成された StateMachine ファイルを編集してビジネスロジックを追加。
6. マシンが進化するにつれて YAML と PlantUML のみ再生成。
```
