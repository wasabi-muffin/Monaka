# KSP annotation reference

The `monaka-transitions` processor reads two source-retention annotations and generates
Kotlin extension functions from them. Both annotations are in `dev.gmvalentino.monaka.core`
and are included in the core `monaka` dependency — no extra dependency is needed to use them.

See [KSP setup](../getting-started/ksp-setup.md) for how to wire the processor into a
Kotlin Multiplatform build.

---

## `@Transition`

```kotlin
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class Transition(vararg val to: KClass<out State> = [])
```

Place on a state **class or data class** to generate one `toXxx()` extension function for each
target in `to`. The function constructs a value of the target type from the receiver.

### Parameters

| Parameter | Description |
|---|---|
| `to` | One or more target state `KClass` values. One function is generated per target. |

### Rules

- The annotated class must implement `State`.
- Each target must implement `State`.
- Applying `@Transition` to a `data object` is valid but produces functions with no parameters,
  since `data object`s have no constructor arguments.
- `@Transition` with an empty `to` array is a no-op — no functions are generated.

### Generated function naming

The generated function name is derived from the **relative path** from the receiver to the
target, stripping the common enclosing sealed interface prefix:

| Receiver | Target | Generated name |
|---|---|---|
| `LoginState.Typing` | `LoginState.Submitting` | `toSubmitting()` |
| `LoginState.Typing` | `LoginState.Authenticated` | `toAuthenticated(user)` |
| `Auth.SignedOut` | `Auth.SigningIn` | `toSigningIn()` |
| `Auth.SignedOut` | `Loading` | `toLoading()` |
| `Loading` | `Auth.SigningIn` | `toAuthSigningIn()` |

When source and target share a common enclosing type, only the non-shared suffix is used. When
they share no enclosing type, the full target name is used with each segment capitalised.

### Parameter generation

For each constructor parameter of the target type:

- If a parameter with the **same name and type** exists on the receiver type, it is given a
  default value of `this.prop`.
- All other parameters are **required** (no default value).

```kotlin
// Source
data class Typing(val username: String, val password: String) : LoginState

// Target
data class Submitting(val username: String, val password: String) : LoginState
data class Authenticated(val username: String) : LoginState
data class Error(val message: String) : LoginState
```

Generated:

```kotlin
// username and password match — both default to this.prop
fun LoginState.Typing.toSubmitting(): LoginState.Submitting =
    LoginState.Submitting(username = this.username, password = this.password)

// username matches — defaults; Authenticated has no password param
fun LoginState.Typing.toAuthenticated(): LoginState.Authenticated =
    LoginState.Authenticated(username = this.username)

// message does not exist on Typing — required parameter
fun LoginState.Typing.toError(message: String): LoginState.Error =
    LoginState.Error(message = message)
```

### Example

```kotlin
@SelfTransition
sealed interface LoginState : State {
    data object Idle : LoginState

    @Transition(
        LoginState.Submitting::class,
        LoginState.Error::class,
    )
    data class Typing(
        val username: String,
        val password: String,
    ) : LoginState

    data class Submitting(
        val username: String,
        val password: String,
    ) : LoginState

    data class Authenticated(val username: String) : LoginState
    data class Error(val message: String) : LoginState
}
```

Usage in a handler:

```kotlin
state<LoginState.Typing> {
    on<LoginAction.Submit> {
        transition(state.toSubmitting())         // username + password copied automatically
    }
}

state<LoginState.Submitting> {
    on<LoginAction.LoginFailed> {
        transition(state.toError(action.message)) // message is required — not on Submitting
    }
}
```

---

## `@SelfTransition`

```kotlin
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class SelfTransition
```

Place on a **sealed class or sealed interface** to generate a single `toSelf()` extension
function. `toSelf()` accepts the type's shared properties (properties present on every direct
sealed subclass with the same name and type) as named parameters — each defaulting to
`this.prop` — and returns a new instance of the same subtype with those values applied via a
`when` dispatch.

### Rules

- The annotated type **must** be sealed. Applying `@SelfTransition` to a non-sealed class is a
  compile-time error reported by the processor.
- Direct subclasses that are `data object`s are handled by returning `this` unchanged (no
  properties to update).
- Nested sealed subtypes (sealed classes inside the root sealed interface) are included in the
  `when` dispatch.

### Shared properties

A property is considered shared if it appears with the **same name and the same type** on every
direct non-object subclass. Only shared properties become parameters of `toSelf()`.

```kotlin
@SelfTransition
sealed interface TimerState : State {
    val autoPause: Boolean    // shared — present on every subclass

    data class Idle(override val autoPause: Boolean = false, …) : TimerState
    data class Running(override val autoPause: Boolean, …) : TimerState
    data class Paused(override val autoPause: Boolean, …) : TimerState
    data class Finished(override val autoPause: Boolean, …) : TimerState
}
```

Generated:

```kotlin
fun TimerState.toSelf(autoPause: Boolean = this.autoPause): TimerState = when (this) {
    is TimerState.Idle     -> copy(autoPause = autoPause)
    is TimerState.Running  -> copy(autoPause = autoPause)
    is TimerState.Paused   -> copy(autoPause = autoPause)
    is TimerState.Finished -> copy(autoPause = autoPause)
}
```

Usage in `onUpdate` or any handler that needs to stay in the same subtype while updating a
shared field:

```kotlin
state<TimerState.Running> {
    on<TimerAction.SetAutoPause> {
        transition(state.toSelf(autoPause = action.enabled))
    }
}
```

### `toSelf()` without shared properties

If the sealed type has no shared properties, `toSelf()` is generated with no parameters and
returns `this` unchanged. This is still useful in `onUpdate` handlers to explicitly record a
no-op transition without hard-coding the subtype:

```kotlin
fun MyState.toSelf(): MyState = when (this) {
    is MyState.Idle    -> this
    is MyState.Loading -> this
    is MyState.Loaded  -> this
}
```

---

## Using both annotations together

The typical pattern — used throughout the sample app — is to place `@SelfTransition` on the
root sealed interface and `@Transition` on each leaf subtype that transitions to another type:

```kotlin
@SelfTransition                          // generates toSelf()
sealed interface TimerState : State {
    val autoPause: Boolean

    @Transition(Running::class)          // generates toRunning(…)
    data class Idle(…) : TimerState

    @Transition(Paused::class, Finished::class, Idle::class)
    data class Running(…) : TimerState   // generates toPaused(…), toFinished(…), toIdle(…)

    @Transition(Running::class, Idle::class)
    data class Paused(…) : TimerState

    @Transition(Idle::class)
    data class Finished(…) : TimerState
}
```

The [stub generator](../gradle-plugin/stub-generator.md) emits exactly this pattern
automatically from your YAML spec when `useTransitionAnnotation = true` (the default), so you
rarely need to write the annotations by hand.

---

## Acknowledgements

The `@Transition` and `@SelfTransition` annotations and their KSP processor were inspired by
[Cream](https://github.com/TBSten/cream) by [TBSten](https://github.com/TBSten) — a KSP library
for declarative copying across sealed-class hierarchies. Cream is licensed under Apache-2.0.
