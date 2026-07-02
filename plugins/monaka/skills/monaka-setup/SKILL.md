---
name: monaka-setup
description: >-
  Add the Monaka Kotlin Multiplatform MVI state-machine library (dev.gmvalentino.monaka) to a
  project. Use when setting up Monaka, adding its Gradle dependencies (monaka, monaka-compose,
  monaka-test, monaka-transitions), configuring the version catalog, choosing targets, or
  resolving setup/"unresolved reference" issues. For writing state machines see
  monaka-state-machines; for the Gradle codegen plugin and KSP see monaka-codegen.
---

# Setting up Monaka

Monaka is a Kotlin Multiplatform MVI state-machine library published to Maven Central under the
group `dev.gmvalentino.monaka`. This skill covers adding it to a module. Full docs:
https://monaka.gmvalentino.dev

## Artifacts

| Artifact | Coordinate | Where | Provides |
|---|---|---|---|
| Core | `dev.gmvalentino.monaka:monaka` | `commonMain` | `State`/`Action`/`Effect`, `Store`, `store {}` / `stateMachine {}` DSL, plugins, relay/registry |
| Compose | `dev.gmvalentino.monaka:monaka-compose` | `commonMain` | `rememberStore`, `toViewStore`, `handleEffects`, `bindLifecycle`, `render` (brings in core transitively) |
| Test DSL | `dev.gmvalentino.monaka:monaka-test` | `commonTest` | `testStore {}`, `expectState`/`expectEffect`/`trigger`, virtual time |
| KSP processor | `dev.gmvalentino.monaka:monaka-transitions` | KSP config | `toXxx()`/`toSelf()` generation from `@Transition`/`@SelfTransition` (see monaka-codegen) |
| Gradle plugin | `dev.gmvalentino.monaka` (plugin id) | `plugins {}` | YAML / PlantUML / stub generators (see monaka-codegen) |

All four library artifacts share the same version. Check the latest on
[Maven Central](https://central.sonatype.com/artifact/dev.gmvalentino.monaka/monaka).

## Recommended: version catalog

`gradle/libs.versions.toml`:

```toml
[versions]
monaka = "0.1.0" # check Maven Central for the latest release

[libraries]
monaka         = { module = "dev.gmvalentino.monaka:monaka",         version.ref = "monaka" }
monaka-compose = { module = "dev.gmvalentino.monaka:monaka-compose", version.ref = "monaka" }
monaka-test    = { module = "dev.gmvalentino.monaka:monaka-test",    version.ref = "monaka" }
```

Module `build.gradle.kts`:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.monaka)
            implementation(libs.monaka.compose) // only if you use the Compose helpers
        }
        commonTest.dependencies {
            implementation(libs.monaka.test)
            implementation(kotlin("test"))
        }
    }
}
```

Prefer `mavenCentral()` in `dependencyResolutionManagement { repositories { … } }`.

## Direct (no catalog)

```kotlin
commonMain.dependencies {
    implementation("dev.gmvalentino.monaka:monaka:0.1.0")
    implementation("dev.gmvalentino.monaka:monaka-compose:0.1.0")
}
commonTest.dependencies {
    implementation("dev.gmvalentino.monaka:monaka-test:0.1.0")
}
```

For an Android-only or JVM-only module, put the same dependencies in `dependencies { … }` /
`main` and `test` source sets instead of `commonMain` / `commonTest`.

## Targets

The core library ships for Android, iOS (arm64, x64, simulatorArm64), JVM, macOS, watchOS, tvOS,
Linux, Windows, JS, and Wasm. You don't configure Monaka's targets — just declare the targets your
module needs; Monaka resolves the matching variant. `monaka-compose` targets Android, iOS, and JVM
(anywhere Compose Multiplatform runs).

## Smallest working example

Put your types and machine in `commonMain`:

```kotlin
import dev.gmvalentino.monaka.core.*
import dev.gmvalentino.monaka.dsl.store

data class CounterState(val count: Int) : State
sealed interface CounterAction : Action {
    data object Increment : CounterAction
    data object Decrement : CounterAction
}
sealed interface CounterEffect : Effect

val counter = store<CounterState, CounterAction, CounterEffect>(scope) {
    initialState(CounterState(0))
    state<CounterState> {
        on<CounterAction.Increment> { transition(state.copy(count = state.count + 1)) }
        on<CounterAction.Decrement> { transition(state.copy(count = state.count - 1)) }
    }
}

counter.dispatch(CounterAction.Increment)
```

`scope` is any `CoroutineScope` — on Android pass `viewModelScope` so the store is canceled when the
ViewModel is cleared. See **monaka-state-machines** for the full authoring guide.

## Core concepts

| Concept | Type | Role |
|---|---|---|
| State | `State` | Immutable snapshot of the machine at a point in time |
| Action | `Action` | Intent dispatched by the UI or system |
| Effect | `Effect` | One-shot side effect (navigation, toast, analytics) |
| Store | `Store<S, A, E>` | Running instance: exposes `state`, `actions`, `effects`, `dispatch` |

`state` is a `StateFlow` (always has a value). `effects` is a `SharedFlow` with **no replay** —
late subscribers miss past emissions.

## Gotchas

- **Subscribe to `effects` before `state`.** Collecting `state` implicitly starts the store and
  fires the initial `onEnter`; any effect it emits is lost if no effect collector is attached yet.
  In Compose, `handleEffects` (from `monaka-compose`) buffers safely — prefer it.
- **Effects need a consumer.** If nothing collects `effects` and the buffer fills, the processing
  loop suspends. Raise `extraBufferCapacity` at construction if you emit effects in bursts.
- **Marker imports** live in `dev.gmvalentino.monaka.core`; the `store` / `stateMachine` factories
  in `dev.gmvalentino.monaka.dsl`.
