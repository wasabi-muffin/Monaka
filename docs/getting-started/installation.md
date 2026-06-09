# Installation

Add the Monaka dependencies to your module's `build.gradle.kts`.

## Core library

```kotlin
implementation("dev.gmvalentino:monaka:<version>")
```

Required for all targets. Provides `State`, `Action`, `Effect`, `Store`, the `store { }` DSL,
`stateMachine { }`, `StateMachineStore`, plugins, and the relay/bridge API.

## Test DSL

```kotlin
// In your commonTest source set
testImplementation("dev.gmvalentino:monaka-test:<version>")
```

Provides `testStore { }` and the assertion DSL (`expectState`, `expectEffect`, `trigger`, …).
See the [testing guide](../guide/testing.md) for usage.

## KSP transition processor (optional)

Generates `toXxx()` / `toSelf()` extension functions from `@Transition` and `@SelfTransition`
annotations placed on your state types by the [stub generator](../gradle-plugin/stub-generator.md).

**Android / JVM only:**

```kotlin
plugins {
    id("com.google.devtools.ksp")
}

dependencies {
    ksp("dev.gmvalentino:monaka-transitions:<version>")
}
```

**Kotlin Multiplatform:** because state types live in `commonMain`, the setup requires a few
extra steps so the generated extensions are visible to all platform compilations. See the
[KSP setup guide](ksp-setup.md) for the full configuration.

## Gradle plugin (optional)

```kotlin
plugins {
    id("dev.gmvalentino.monaka")
}
```

Adds three Gradle tasks for code generation from your state machine DSL. See the
[Gradle plugin](../gradle-plugin/yaml-generator.md) section for full configuration.
