# Stub Generator

The stub generator reads `.yaml` files produced by the [YAML generator](yaml-generator.md) and
emits four Kotlin source files per machine:

| File | Contents |
|---|---|
| `{Name}State.kt` | Sealed interface hierarchy for all states. |
| `{Name}Action.kt` | Sealed interface listing all action types. |
| `{Name}Effect.kt` | Sealed interface listing all effect types. |
| `{Name}StateMachine.kt` | `stateMachine { }` DSL wired to the generated types. |

This is a scaffolding step — the generated files are starting points that you own and edit freely.
Re-running with `--replace` overwrites them; the default is to skip files that already exist.

---

## Setup

Apply the Monaka Gradle plugin and configure the `monakaStubs` extension:

```kotlin
// build.gradle.kts
plugins {
    id("dev.gmvalentino.monaka")
}

monakaStubs {
    // Path to a single .yaml file or a directory to scan recursively.
    // Default: project root directory (scans for any .yaml file).
    input.set("${layout.buildDirectory.get()}/monaka-yaml")

    // Where to write the generated .kt files.
    // Default: same directory as each source YAML file.
    outputDir.set(layout.projectDirectory.dir("src/commonMain/kotlin/com/example/auth"))

    // Generation style: CLASS (default) or FACTORY.
    style.set(com.example.gradle.StubStyle.CLASS)

    // Overwrite existing files. Default: false.
    replace.set(false)

    // Emit @SelfTransition / @Transition annotations on the state sealed interface.
    // Default: true. Set to false if you are not using monaka-transitions / KSP.
    useTransitionAnnotation.set(true)
}
```

---

## Running

```bash
# Use extension defaults
./gradlew generateMonakaStubs

# Override options at the command line
./gradlew generateMonakaStubs \
    --input=build/monaka-yaml/Login.yaml \
    --output=src/commonMain/kotlin/com/example/auth \
    --style=class \
    --replace=true \
    --use-transition-annotation=true
```

### CLI options

| Option | Default | Description |
|---|---|---|
| `--input` | extension `input` | Path to a `.yaml` file or directory. |
| `--output` | extension `outputDir`, or YAML file's directory | Directory to write generated `.kt` files. |
| `--style` | `class` | Generation style: `class` or `factory` (see below). |
| `--replace` | `false` | When `true`, overwrite files that already exist. |
| `--use-transition-annotation` | `true` | Emit `@SelfTransition` / `@Transition` on the state sealed interface. |

CLI options take precedence over the extension when both are set.

---

## Generation styles

### `CLASS` (default)

Generates a concrete class that delegates to `stateMachine { }`. Use this style when you need to
inject dependencies (repositories, use-cases) as constructor parameters:

```kotlin
class LoginStateMachine(
    private val loginRepository: LoginRepository,
) : StateMachine<LoginState, LoginAction, LoginEffect> by stateMachine(
    builder = {
        initialState(LoginState.Idle)

        state<LoginState> {
            on<LoginAction.Logout> {
                transition(LoginState.Idle)
                sideEffect(LoginEffect.NavigateToLogin)
            }
        }

        state<LoginState.Idle> {
            on<LoginAction.Submit> {
                task("login", autoCancel = true) {
                    dispatch(LoginAction.LoginSucceeded)
                }
                transition(LoginState.Submitting)
            }
        }

        // … remaining states
    }
)
```

### `FACTORY`

Generates a top-level `val` holding a `StateMachine` instance. Use this style for machines with
no external dependencies, or when you prefer a functional, closure-captured approach:

```kotlin
val loginStateMachine = stateMachine<LoginState, LoginAction, LoginEffect> {
    initialState(LoginState.Idle)

    state<LoginState.Idle> {
        on<LoginAction.Submit> {
            transition(LoginState.Submitting)
        }
    }

    // … remaining states
}
```

---

## Generated state types

### Simple machine (single state)

A machine whose YAML has no substate hierarchy generates a single `data object`:

```kotlin
@SelfTransition
sealed interface CounterState : State {
    data object Counter : CounterState
}
```

### Hierarchical machine

A machine with multiple states generates a full sealed interface hierarchy. If
`useTransitionAnnotation` is `true`, `@SelfTransition` is placed on the root sealed interface and
`@Transition(…)` is placed on each substate that transitions to other states — these annotations
drive the `monaka-transitions` KSP processor to generate `toXxx()` extension functions:

```kotlin
@SelfTransition
sealed interface LoginState : State {
    data object Idle : LoginState
    @Transition(LoginState.Submitting::class)
    data object Submitting : LoginState
    data object Authenticated : LoginState
}
```

To disable the annotations (e.g. if you are not using KSP), set `useTransitionAnnotation = false`
or pass `--use-transition-annotation=false`. The state hierarchy is still generated; only the
annotation lines are omitted.

---

## Package inference

When no explicit `--output` directory is specified, the generator writes files next to the source
YAML. It also attempts to infer the Kotlin package declaration by scanning parent directories for
a known source-set root (`src/commonMain/kotlin/`, `src/main/kotlin/`, etc.). If inference
succeeds, `package com.example.auth` is prepended to each generated file automatically.

If inference fails (e.g. the YAML is in `build/monaka-yaml/` with no source-set structure above
it), the generated files have no `package` declaration. Move them into a source directory
afterwards, or set an explicit `--output` path under your source tree so inference can resolve the
package.

---

## Typical workflow

```
1. Write state machines using the Monaka DSL.
2. Run ./gradlew generateMonakaYaml    → produces build/monaka-yaml/Login.yaml
3. Run ./gradlew generateMonakaStubs   → produces LoginState.kt, LoginAction.kt, …
4. Move / copy generated files into your source tree (if outputDir is not already there).
5. Edit the generated StateMachine file to fill in real business logic.
6. Re-generate only the YAML and PlantUML as the machine evolves — do not re-generate stubs
   unless you want to reset to a clean scaffold (use --replace to overwrite).
```
