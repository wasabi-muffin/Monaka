# KSP setup for Kotlin Multiplatform

The `monaka-transitions` processor uses [KSP (Kotlin Symbol Processing)](https://github.com/google/ksp)
to generate `toXxx()` and `toSelf()` extension functions from `@Transition` and `@SelfTransition`
annotations on your state types.

State types live in `commonMain`. The generated extensions must therefore also be available from
`commonMain` so you can call `state.toLoading()` inside a `stateMachine { }` handler that lives
in a shared source set. This requires a small amount of extra Gradle wiring compared with a
plain Android or JVM project.

---

## Version compatibility

The KSP plugin version must be compatible with the Kotlin version used in your project. The
version prefix of the KSP plugin matches the Kotlin version:

| Kotlin version | KSP version prefix |
|---|---|
| 2.1.x | `2.1.x-…` |
| 2.0.x | `2.0.x-…` |
| 1.9.x | `1.9.x-…` |

Check [github.com/google/ksp/releases](https://github.com/google/ksp/releases) for the latest
compatible release. Use the version catalog to keep them in sync:

```toml
# gradle/libs.versions.toml
[versions]
kotlin = "2.1.0"
ksp    = "2.1.0-1.0.29"   # must share the same Kotlin prefix

[plugins]
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

---

## Apply the plugin

Add the KSP plugin to every module that contains annotated state types:

```kotlin
// build.gradle.kts
plugins {
    kotlin("multiplatform")
    id("com.google.devtools.ksp")
}
```

---

## Add the processor

Because state types are in `commonMain`, you need to:

1. Run the processor against the **common metadata compilation** so the generated files are
   produced from the shared sources.
2. Expose the generated output directory as a **`commonMain` source root** so every platform
   compilation can see the extensions.
3. Ensure the metadata KSP task runs **before** any platform compilation that depends on
   `commonMain`.

```kotlin
// build.gradle.kts
dependencies {
    // Run the processor against the commonMain metadata compilation
    add("kspCommonMainMetadata", "dev.gmvalentino:monaka-transitions:<version>")
}

kotlin {
    sourceSets {
        commonMain {
            // Expose the KSP-generated directory as a source root
            kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
        }
    }
}

// Every platform compilation that includes commonMain must wait for KSP to finish
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    if (name != "kspCommonMainKotlinMetadata") {
        dependsOn("kspCommonMainKotlinMetadata")
    }
}
```

This is the minimal setup. The `dependsOn` block is required because Gradle does not
automatically know that other compilations (e.g. `compileKotlinAndroid`,
`compileKotlinIosArm64`) depend on the output of `kspCommonMainKotlinMetadata`.

---

## Targets

The processor only needs to run once against `commonMainMetadata` — you do **not** need to add
`kspAndroid`, `kspIosArm64`, etc. when the annotated classes all live in `commonMain`.

If you have annotated state types in a platform-specific source set (rare), also add the
processor for those targets:

```kotlin
dependencies {
    add("kspCommonMainMetadata", "dev.gmvalentino:monaka-transitions:<version>")

    // Only needed for platform-specific annotated types:
    add("kspAndroid",              "dev.gmvalentino:monaka-transitions:<version>")
    add("kspIosArm64",             "dev.gmvalentino:monaka-transitions:<version>")
    add("kspIosSimulatorArm64",    "dev.gmvalentino:monaka-transitions:<version>")
    add("kspIosX64",               "dev.gmvalentino:monaka-transitions:<version>")
    add("kspJvm",                  "dev.gmvalentino:monaka-transitions:<version>")
}
```

---

## Complete example

A typical multiplatform module with Android and iOS targets:

```kotlin
// build.gradle.kts
plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("com.google.devtools.ksp")
}

kotlin {
    androidTarget()
    iosArm64()
    iosSimulatorArm64()
    iosX64()
    jvm()

    sourceSets {
        commonMain {
            dependencies {
                implementation("dev.gmvalentino:monaka:<version>")
            }
            // KSP-generated extensions live here
            kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
        }
        commonTest {
            dependencies {
                implementation("dev.gmvalentino:monaka-test:<version>")
            }
        }
    }
}

dependencies {
    add("kspCommonMainMetadata", "dev.gmvalentino:monaka-transitions:<version>")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    if (name != "kspCommonMainKotlinMetadata") {
        dependsOn("kspCommonMainKotlinMetadata")
    }
}
```

---

## Using a version catalog

```toml
# gradle/libs.versions.toml
[versions]
kotlin           = "2.1.0"
ksp              = "2.1.0-1.0.29"
monaka           = "<version>"

[libraries]
monaka                = { module = "dev.gmvalentino:monaka",              version.ref = "monaka" }
monaka-test           = { module = "dev.gmvalentino:monaka-test",         version.ref = "monaka" }
monaka-transitions    = { module = "dev.gmvalentino:monaka-transitions",  version.ref = "monaka" }

[plugins]
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

```kotlin
// build.gradle.kts
plugins {
    alias(libs.plugins.ksp)
}

dependencies {
    add("kspCommonMainMetadata", libs.monaka.transitions)
}
```

---

## Verifying the setup

After a successful build, the generated files appear at:

```
build/generated/ksp/metadata/commonMain/kotlin/
└── com/example/feature/
    ├── LoginStateTransitions.kt    ← toXxx() functions from @Transition
    └── LoginStateTransitions.kt    ← toSelf() function from @SelfTransition
```

Each annotated state type produces one `*Transitions.kt` file. You can inspect these files to
confirm the generated function names before using them in handlers.

---

## What gets generated

### `@Transition` → `toXxx()`

Annotate a leaf state with `@Transition` listing its possible transition targets:

```kotlin
@SelfTransition
sealed interface LoginState : State {
    data object Idle : LoginState

    @Transition(LoginState.Submitting::class)
    data class Typing(val username: String, val password: String) : LoginState

    data object Submitting : LoginState
    data class Authenticated(val user: User) : LoginState
}
```

The processor generates one extension per target, with parameters matching the target's
constructor. Properties shared with the source type default to `this.prop`:

```kotlin
// Generated
fun LoginState.Typing.toSubmitting(): LoginState.Submitting = LoginState.Submitting
fun LoginState.Typing.toAuthenticated(user: User): LoginState.Authenticated =
    LoginState.Authenticated(user = user)
```

Use them in handlers:

```kotlin
state<LoginState.Typing> {
    on<LoginAction.Submit> {
        transition(state.toSubmitting())
    }
}
```

### `@SelfTransition` → `toSelf()`

Annotate the root sealed interface with `@SelfTransition`. The processor generates a single
`toSelf()` extension that dispatches over all sealed subtypes and copies shared properties:

```kotlin
// Generated — accepts every shared property as a named parameter with this.prop defaults
fun LoginState.toSelf(/* shared properties */): LoginState = when (this) {
    is LoginState.Idle          -> this
    is LoginState.Typing        -> copy(/* shared props */)
    is LoginState.Submitting    -> this
    is LoginState.Authenticated -> this
}
```

Use it in `onUpdate` handlers or any handler that needs to stay in the same subtype while
updating shared properties:

```kotlin
state<LoginState> {
    onUpdate {
        transition(state.toSelf())
    }
}
```

---

## Troubleshooting

**Generated files are not found at compile time**

The `srcDir` call must be present and the `dependsOn` block must be configured. Without
`dependsOn`, Gradle may compile platform targets before `kspCommonMainKotlinMetadata` runs,
causing "unresolved reference" errors. Verify both are in place and run a clean build:

```bash
./gradlew clean :your-module:kspCommonMainKotlinMetadata
```

**`kspCommonMainKotlinMetadata` task not found**

This task is only created when the KSP plugin is applied *and* a `commonMain` source set exists.
Confirm the `kotlin("multiplatform")` plugin is applied before `com.google.devtools.ksp`.

**KSP version mismatch**

If you see errors like `KSP can only be applied to a project with the Kotlin plugin`, the KSP
plugin version is incompatible with the Kotlin version. Check that the numeric prefix of the KSP
version matches your Kotlin version exactly (e.g. Kotlin `2.1.0` → KSP `2.1.0-1.0.x`).
