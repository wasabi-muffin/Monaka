---
name: monaka-codegen
description: >-
  Generate Monaka code. Two independent tools: (1) the @Transition/@SelfTransition KSP processor
  (monaka-transitions) that generates toXxx()/toSelf() state-transition extensions, and (2) the
  dev.gmvalentino.monaka Gradle plugin with YAML, PlantUML, and stub generators. Use when setting
  up KSP for transition helpers, generating diagrams from a machine, or scaffolding State/Action/
  Effect/StateMachine files from a spec. For hand-writing machines see monaka-state-machines.
---

# Monaka code generation

Two separate, optional tools:

- **`monaka-transitions`** (KSP) — turns `@Transition` / `@SelfTransition` annotations on your state
  types into `toXxx()` / `toSelf()` builder extensions. Reduces boilerplate when constructing the
  next state.
- **`dev.gmvalentino.monaka` Gradle plugin** — scans `stateMachine {}` DSL and produces a YAML
  spec, PlantUML diagrams, and Kotlin stubs.

Full docs: https://monaka.gmvalentino.dev (Getting Started → KSP Setup; Gradle Plugin section).

---

## Part 1 — `@Transition` KSP processor

### What it generates

```kotlin
import dev.gmvalentino.monaka.core.State
import dev.gmvalentino.monaka.core.Transition
import dev.gmvalentino.monaka.core.SelfTransition

@SelfTransition                                   // on the root sealed type → toSelf()
sealed interface LoginState : State {
    data object Idle : LoginState

    @Transition(LoginState.Submitting::class)     // on a substate → one toXxx() per target
    data class Typing(val username: String, val password: String) : LoginState

    data object Submitting : LoginState
    data class Authenticated(val user: User) : LoginState
}
```

produces, in `build/generated/…/LoginStateTransitions.kt`:

```kotlin
fun LoginState.Typing.toSubmitting(): LoginState.Submitting = LoginState.Submitting
// shared properties (same name + type) default to this.prop; new ones are required parameters
fun LoginState.Typing.toAuthenticated(user: User): LoginState.Authenticated =
    LoginState.Authenticated(user = user)

// @SelfTransition → copy shared props, dispatch over subtypes
fun LoginState.toSelf(/* shared props, each defaulting to this.prop */): LoginState = when (this) { … }
```

Use them in handlers — cleaner than repeating constructors:

```kotlin
state<LoginState.Typing> {
    on<LoginAction.Submit> { transition(state.toSubmitting()) }
    on<LoginAction.UpdateCredentials> { transition(state.toSelf(username = action.username)) }
}
```

- `@Transition(A::class, B::class)` → `toA()`, `toB()`. Empty `@Transition` on a sealed type also
  generates `toSelf()`.
- `@SelfTransition` is restricted to **sealed** classes/interfaces (compile error otherwise).

### Setup — plain Android / JVM module

```kotlin
plugins { id("com.google.devtools.ksp") }
dependencies { ksp("dev.gmvalentino.monaka:monaka-transitions:<version>") }
```

### Setup — Kotlin Multiplatform (state types in `commonMain`)

State types live in `commonMain`, so the generated extensions must be visible to every platform
compilation. Three moving parts:

```kotlin
plugins {
    kotlin("multiplatform")
    id("com.google.devtools.ksp")
}

kotlin {
    sourceSets {
        commonMain {
            // 2. expose the generated dir as a common source root
            kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
        }
    }
}

dependencies {
    // 1. run the processor against the common metadata compilation
    add("kspCommonMainMetadata", "dev.gmvalentino.monaka:monaka-transitions:<version>")
}

// 3. make every platform compilation wait for KSP metadata to finish
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    if (name != "kspCommonMainKotlinMetadata") dependsOn("kspCommonMainKotlinMetadata")
}
```

You do **not** need `kspAndroid` / `kspIosArm64` / etc. when the annotated types are all in
`commonMain` — the metadata pass covers them. Only add per-target `ksp*` configs for annotated types
that live in a platform source set.

### Version compatibility (important)

The KSP plugin version's prefix must match your Kotlin version exactly (KSP is versioned as
`<kotlin-version>-<ksp-build>`):

```toml
[versions]
kotlin = "2.1.0"
ksp    = "2.1.0-1.0.29"   # prefix MUST equal the Kotlin version
```

Mismatch symptoms: `KSP can only be applied to a project with the Kotlin plugin`.

### Troubleshooting

- **`unresolved reference: toXxx`** — the `srcDir` and/or the `dependsOn` block is missing, so
  platform code compiled before KSP ran. Add both, then `./gradlew clean :module:kspCommonMainKotlinMetadata`.
- **`kspCommonMainKotlinMetadata` task not found** — apply `kotlin("multiplatform")` *before*
  `com.google.devtools.ksp`, and ensure a `commonMain` source set exists.

---

## Part 2 — Gradle plugin (YAML / PlantUML / stubs)

Apply once; it adds three tasks. Configure only the generators you use.

```kotlin
plugins { id("dev.gmvalentino.monaka") }
```

| Task | Extension | Reads | Writes |
|---|---|---|---|
| `generateMonakaYaml` | `monakaYamlGenerator` | your `stateMachine {}` Kotlin sources | one `.yaml` spec per machine |
| `generateMonakaPuml` | `monakaPumlGenerator` | the `.yaml` files (or sources) | `.puml` state diagrams |
| `generateMonakaStubs` | `monakaStubGenerator` | a `.yaml` file/dir | `{Name}State/Action/Effect/StateMachine.kt` |

### YAML — machine-readable spec

```kotlin
monakaYamlGenerator {
    sources.setFrom(fileTree("src/commonMain/kotlin") { include("**/*.kt") })
    yamlOutputDir.set(layout.buildDirectory.dir("monaka-yaml"))
}
```
```bash
./gradlew generateMonakaYaml
```

The scanner is regex-based (no compiler); it recognizes `stateMachine<S, A, E> { … }` and
`class X : StateMachine<S, A, E> by stateMachine(builder = { … })`, naming the machine after the
variable/class.

### PlantUML — diagrams

```kotlin
monakaPumlGenerator { pumlOutputDir.set(layout.buildDirectory.dir("monaka-puml")) }
```
```bash
./gradlew generateMonakaYaml && ./gradlew generateMonakaPuml
```

If both a hand-edited `Machine.yaml` and an auto-generated `Machine.gen.yaml` exist,
`generateMonakaPuml` prefers the hand-edited one — so you can enrich diagrams by editing YAML.

### Stubs — scaffold a new machine from a spec

```kotlin
monakaStubGenerator {
    input.set("${layout.buildDirectory.get()}/monaka-yaml")
    outputDir.set(layout.projectDirectory.dir("src/commonMain/kotlin/com/example/auth"))
    style.set(com.example.gradle.StubStyle.CLASS)   // CLASS (default) or FACTORY
    replace.set(false)                              // true = overwrite existing files
    useTransitionAnnotation.set(true)               // emit @Transition/@SelfTransition (Part 1)
}
```
```bash
./gradlew generateMonakaStubs   # or override: --input=… --output=… --style=class --replace=true
```

- **`CLASS`** style → `class NameStateMachine(deps) : StateMachine<…> by stateMachine(builder = { … })`
  — use when you inject dependencies.
- **`FACTORY`** style → a top-level `val nameStateMachine = stateMachine<…> { … }` — for
  dependency-free machines.
- Stubs are yours to edit; by default existing files are skipped (pass `--replace` to reset).
- Set `useTransitionAnnotation = false` if you are **not** using the KSP processor from Part 1.

### Typical workflow

```
1. Write (or scaffold) machines with the DSL.
2. generateMonakaYaml  → build/monaka-yaml/*.yaml
3. generateMonakaPuml  → diagrams for review/docs
4. generateMonakaStubs → scaffold new State/Action/Effect/StateMachine files (once), then edit.
```
