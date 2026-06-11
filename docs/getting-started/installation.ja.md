# インストール

モジュールの `build.gradle.kts` に Monaka の依存関係を追加します。

## コアライブラリ

```kotlin
implementation("dev.gmvalentino.monaka:monaka:<version>")
```

すべてのターゲットで必要です。`State`・`Action`・`Effect`・`Store`・`store { }` DSL・`stateMachine { }`・`StateMachineStore`・プラグイン・リレー/ブリッジ API を提供します。

## Compose インテグレーション

```kotlin
implementation("dev.gmvalentino.monaka:monaka-compose:<version>")
```

Compose Multiplatform ヘルパーを提供します: `rememberStore`・`toViewStore`・`handleEffects`・`bindLifecycle`・`render`・`ViewStore`・`RenderScope`。Android・iOS・JVM に対応。`:monaka` を推移的に取り込みます。[Compose インテグレーションガイド](../guide/compose.md)を参照してください。

## テスト DSL

```kotlin
// commonTest ソースセットに追加
testImplementation("dev.gmvalentino.monaka:monaka-test:<version>")
```

`testStore { }` とアサーション DSL（`expectState`・`expectEffect`・`trigger` など）を提供します。使用方法は[テストガイド](../guide/testing.md)を参照してください。

## KSP トランジションプロセッサー（オプション）

[スタブジェネレーター](../gradle-plugin/stub-generator.md)が配置した `@Transition` および `@SelfTransition` アノテーションから `toXxx()` / `toSelf()` 拡張関数を生成します。

**Android / JVM のみ:**

```kotlin
plugins {
    id("com.google.devtools.ksp")
}

dependencies {
    ksp("dev.gmvalentino.monaka:monaka-transitions:<version>")
}
```

**Kotlin Multiplatform:** ステート型は `commonMain` に存在するため、生成された拡張をすべてのプラットフォームコンパイルから参照できるように追加の Gradle 設定が必要です。詳細は [KSP セットアップガイド](ksp-setup.md)を参照してください。

## Gradle プラグイン（オプション）

```kotlin
plugins {
    id("dev.gmvalentino.monaka")
}
```

ステートマシン DSL からコードを生成する3つの Gradle タスクを追加します。詳細は [Gradle プラグイン](../gradle-plugin/yaml-generator.md)セクションを参照してください。
