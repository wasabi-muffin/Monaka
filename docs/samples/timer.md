# Timer

**Source:** `sample/shared/…/examples/timer/`

The timer models a countdown with pause/resume support and optional auto-pause when the app
goes to the background. It demonstrates keyed tasks with `autoCancel`, lifecycle hooks
(`onPause`/`onResume`), `@SelfTransition`/`toSelf()` for updating shared properties, and how to
thread a flag across every state without duplicating handler logic.

---

## Types

```kotlin
@SelfTransition
sealed interface TimerState : State {
    val autoPause: Boolean          // shared property — present on every subclass

    @Transition(Running::class)
    data class Idle(
        val durationSeconds: Int = 60,
        override val autoPause: Boolean = false,
    ) : TimerState

    @Transition(Paused::class, Finished::class, Idle::class)
    data class Running(
        val remainingSeconds: Int,
        val totalSeconds: Int,
        override val autoPause: Boolean,
    ) : TimerState

    @Transition(Running::class, Idle::class)
    data class Paused(
        val remainingSeconds: Int,
        val totalSeconds: Int,
        override val autoPause: Boolean,
        val pausedByLifecycle: Boolean = false,
    ) : TimerState

    @Transition(Idle::class)
    data class Finished(val totalSeconds: Int, override val autoPause: Boolean) : TimerState
}

sealed interface TimerAction : Action {
    data class  SetDuration(val seconds: Int) : TimerAction
    data class  SetAutoPause(val enabled: Boolean) : TimerAction
    data object Start : TimerAction
    data object Tick  : TimerAction
    data object Pause : TimerAction
    data object PauseForLifecycle : TimerAction   // internal — dispatched by onPause hook
    data object Resume : TimerAction
    data object Reset  : TimerAction
}

sealed interface TimerEffect : Effect {
    data object Completed : TimerEffect
}
```

---

## State machine (abridged)

```kotlin
class TimerStateMachine : StateMachine<TimerState, TimerAction, TimerEffect> by stateMachine(builder = {
    initialState(TimerState.Idle())

    state<TimerState.Idle> {
        on<TimerAction.SetDuration> { transition(state.copy(durationSeconds = action.seconds.coerceAtLeast(1))) }
        on<TimerAction.SetAutoPause> { transition(state.toSelf(autoPause = action.enabled)) }
        on<TimerAction.Start> { transition(state.toRunning(remainingSeconds = state.durationSeconds, totalSeconds = state.durationSeconds)) }
    }

    state<TimerState.Running> {
        onEnter {
            task(key = "tick", autoCancel = true) {
                while (true) { delay(1_000); dispatch(TimerAction.Tick) }
            }
        }
        on<TimerAction.Tick> {
            if (state.remainingSeconds <= 1) {
                transition(state.toFinished()); sideEffect(TimerEffect.Completed)
            } else {
                transition(state.copy(remainingSeconds = state.remainingSeconds - 1))
            }
        }
        on<TimerAction.Pause> { transition(state.toPaused(pausedByLifecycle = false)) }
        on<TimerAction.PauseForLifecycle> { transition(state.toPaused(pausedByLifecycle = true)) }
        on<TimerAction.SetAutoPause> { transition(state.toSelf(autoPause = action.enabled)) }
        on<TimerAction.Reset> { transition(state.toIdle(durationSeconds = state.totalSeconds)) }

        onPause { if (state.autoPause) dispatch(TimerAction.PauseForLifecycle) }
    }

    state<TimerState.Paused> {
        on<TimerAction.Resume> { transition(state.toRunning()) }
        on<TimerAction.SetAutoPause> { transition(state.toSelf(autoPause = action.enabled)) }
        on<TimerAction.Reset> { transition(state.toIdle(durationSeconds = state.totalSeconds)) }

        onResume { if (state.pausedByLifecycle) dispatch(TimerAction.Resume) }
    }

    state<TimerState.Finished> {
        on<TimerAction.Reset> { transition(state.toIdle(durationSeconds = state.totalSeconds)) }
    }

    install(LoggingPlugin(tag = "Timer"))
})
```

---

## Patterns demonstrated

### Keyed task with `autoCancel`

The tick loop is launched in `Running.onEnter` with `key = "tick"` and `autoCancel = true`.
`autoCancel` tells the runtime to cancel the job automatically when the state type changes —
no explicit `onExit { cancel("tick") }` needed. Re-entering `Running` (after a resume) starts
a fresh `task("tick")` that cancels the previous one via key replacement:

```kotlin
onEnter {
    task(key = "tick", autoCancel = true) {
        while (true) { delay(1_000); dispatch(TimerAction.Tick) }
    }
}
```

### Separating user pause from lifecycle pause

The machine distinguishes between a user-initiated pause and a lifecycle pause using a dedicated
`PauseForLifecycle` action and the `pausedByLifecycle` flag on `Paused`. Only a lifecycle-caused
pause auto-resumes on `ON_RESUME`:

```kotlin
// Running — lifecycle hook dispatches the internal action
onPause { if (state.autoPause) dispatch(TimerAction.PauseForLifecycle) }

// Paused — only auto-resume if the pause was lifecycle-driven
onResume { if (state.pausedByLifecycle) dispatch(TimerAction.Resume) }
```

User tapping Pause dispatches `TimerAction.Pause` → `pausedByLifecycle = false` → no
auto-resume. This keeps the machine correct regardless of whether auto-pause is enabled.

### `toSelf()` for shared property updates

`SetAutoPause` is valid in every state and only changes the shared `autoPause` field. Instead
of writing a `copy(autoPause = …)` branch for each state type, `toSelf()` dispatches over the
sealed hierarchy and returns the right subtype automatically:

```kotlin
on<TimerAction.SetAutoPause> {
    transition(state.toSelf(autoPause = action.enabled))
}
```

This handler is registered identically in `Idle`, `Running`, and `Paused` — the only difference
is which `state<T>` block it sits in. With `toSelf()`, the body stays identical across all three.

### Opt-in lifecycle integration

The `autoPause` flag is threaded through every state so it persists across transitions. The
screen forwards `onLifecycleEvent(LifecycleEvent.OnPause/OnResume)` regardless of the flag
value — the machine decides whether to act based on `state.autoPause`, keeping the UI layer
free of conditional logic.
