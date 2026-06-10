# YAML Generator

The YAML generator scans Kotlin source files for `stateMachine { }` DSL blocks and emits one
`.yaml` file per machine. The output serves as a machine-readable spec — human documentation,
input to the [stub generator](stub-generator.md), and input to the [PlantUML generator](puml-generator.md).

---

## Setup

Apply the Monaka Gradle plugin in the module that contains your state machine sources:

```kotlin
// build.gradle.kts
plugins {
    id("dev.gmvalentino.monaka")
}
```

Configure the `monakaYamlGenerator` extension to point at your source files:

```kotlin
monakaYamlGenerator {
    // Files to scan — adjust the glob to match your source sets.
    sources.setFrom(fileTree("src/commonMain/kotlin") { include("**/*.kt") })

    // Where .yaml files are written. Default: alongside each source file.
    yamlOutputDir.set(layout.buildDirectory.dir("monaka-yaml"))
}
```

---

## Running

```bash
./gradlew generateMonakaYaml
```

One `.yaml` file is written to `yamlOutputDir` for every `stateMachine { }` block found in the
configured sources. The task is cacheable — it re-runs only when input sources change.

---

## Output format

Given a `LoginStateMachine` defined with states `Idle`, `Submitting`, and `Authenticated`, the
generator produces a file named `LoginStateMachine.yaml`:

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

### Top-level keys

| Key | Description |
|---|---|
| `name` | Machine name derived from the DSL (e.g. `Login` from `LoginStateMachine`). |
| `initial` | Simple name of the initial state. |
| `states` | Map of state path → state node. |

### State node keys

| Key | Description |
|---|---|
| `onEnter` | Hook called when the machine enters this state. |
| `onExit` | Hook called when the machine leaves this state. |
| `onUpdate` | Hook called when the machine re-enters the same state type with a new value. |
| `on*` (lifecycle) | Any lifecycle hook: `onPause`, `onResume`, `onStart`, `onStop`, etc. |
| `<ActionName>` | Handler for a dispatched action. |

An empty state node is written as `{}`.

### Handler / hook body keys

| Key | Description |
|---|---|
| `transition` | Inline list of target state names: `[Loading]`. |
| `effect` | Inline list of effect names: `[ShowError, Navigate]`. |
| `dispatch` | Inline list of action names dispatched from this handler. |
| `task` | Async task descriptor (see below). |
| `reject` | `true` when the handler calls `reject()` — no transition or effect. |

### Task descriptor keys

| Key | Description |
|---|---|
| `key` | Named key for the task, used by `cancel("key")`. Omitted for anonymous tasks. |
| `autoCancel` | `true` when the task was launched with `autoCancel = true`. |
| `dispatch` | Inline list of actions the task body dispatches on completion. |

### Hierarchical states

States that contain substates (sealed hierarchies) are represented using dot-path notation so
every entry sits at the top level under `states:`:

```yaml
states:
  Auth: {}           # catch-all / parent state
  Auth.SignedOut:
    Login:
      transition: [Auth.SigningIn]
  Auth.SigningIn: {}
```

---

## What the scanner recognises

The parser uses regex-based heuristics — no full Kotlin compiler. It recognises two patterns:

```kotlin
// Explicit type parameters
val machine = stateMachine<MyState, MyAction, MyEffect> { … }

// Supertype inference (class delegates to stateMachine)
class MyStateMachine : StateMachine<MyState, MyAction, MyEffect> by stateMachine(builder = { … })
```

The machine name is inferred from the variable/class name that holds the `stateMachine { }` call.
Files that do not contain either pattern produce no output.
