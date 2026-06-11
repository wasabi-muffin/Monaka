# KotlinマルチプラットフォームのKSPセットアップ

`monaka-transitions`プロセッサーは[KSP (Kotlin Symbol Processing)](https://github.com/google/ksp)を使用して、状態型に付与された`@Transition`および`@SelfTransition`アノテーションから`toXxx()`および`toSelf()`拡張関数を生成します。

状態型は`commonMain`に配置されます。そのため、生成された拡張関数も`commonMain`から利用できる必要があります。これにより、共有ソースセット内の`stateMachine { }`ハンドラーで`state.toLoading()`を呼び出せるようになります。これは、通常のAndroidまたはJVMプロジェクトと比較して、Gradleの追加設定が少し必要です。

---

## バージョン互換性

KSPプラグインのバージョンは、プロジェクトで使用しているKotlinのバージョンと互換性がなければなりません。KSPプラグインのバージョンプレフィックスはKotlinのバージョンと一致します:

| Kotlinバージョン | KSPバージョンプレフィックス |
|---|---|
| 2.1.x | `2.1.x-…` |
| 2.0.x | `2.0.x-…` |
| 1.9.x | `1.9.x-…` |

最新の互換リリースについては[github.com/google/ksp/releases](https://github.com/google/ksp/releases)を確認してください。バージョンカタログを使用して同期を保ちます:

```toml
# gradle/libs.versions.toml
[versions]
kotlin = "2.1.0"
ksp    = "2.1.0-1.0.29"   # must share the same Kotlin prefix

[plugins]
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

---

## プラグインの適用

アノテーション付きの状態型を含むすべてのモジュールにKSPプラグインを追加します:

```kotlin
// build.gradle.kts
plugins {
    kotlin("multiplatform")
    id("com.google.devtools.ksp")
}
```

---

## プロセッサーの追加

状態型が`commonMain`にあるため、以下が必要です:

1. 共有ソースから生成ファイルが生成されるよう、**共通メタデータのコンパイル**に対してプロセッサーを実行する。
2. すべてのプラットフォームコンパイルが拡張関数を参照できるよう、生成された出力ディレクトリを**`commonMain`ソースルート**として公開する。
3. `commonMain`に依存するプラットフォームコンパイルよりも**前に**メタデータKSPタスクが実行されるようにする。

```kotlin
// build.gradle.kts
dependencies {
    add("kspCommonMainMetadata", "dev.gmvalentino.monaka:monaka-transitions:<version>")
}

kotlin {
    sourceSets {
        commonMain {
            kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    if (name != "kspCommonMainKotlinMetadata") {
        dependsOn("kspCommonMainKotlinMetadata")
    }
}
```

---

## ターゲット

プロセッサーは`commonMainMetadata`に対して一度だけ実行する必要があります。アノテーション付きクラスがすべて`commonMain`に存在する場合、`kspAndroid`や`kspIosArm64`などを追加する必要は**ありません**。

---

## セットアップの確認

ビルドが成功すると、生成されたファイルは以下の場所に配置されます:

```
build/generated/ksp/metadata/commonMain/kotlin/
└── com/example/feature/
    ├── LoginStateTransitions.kt
    └── LoginStateTransitions.kt
```

---

## トラブルシューティング

**コンパイル時に生成ファイルが見つからない**

`srcDir`の呼び出しが存在し、`dependsOn`ブロックが設定されている必要があります。両方が設定されていることを確認し、クリーンビルドを実行してください。

**`kspCommonMainKotlinMetadata`タスクが見つからない**

このタスクはKSPプラグインが適用され、かつ`commonMain`ソースセットが存在する場合にのみ作成されます。

**KSPバージョンの不一致**

KSPバージョンの数値プレフィックスがKotlinバージョンと正確に一致しているか確認してください。
