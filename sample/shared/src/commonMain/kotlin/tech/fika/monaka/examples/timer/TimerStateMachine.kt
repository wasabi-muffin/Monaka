package tech.fika.monaka.examples.timer

import kotlinx.coroutines.delay
import tech.fika.monaka.dsl.StateMachine
import tech.fika.monaka.dsl.stateMachine
import tech.fika.monaka.plugin.LoggingPlugin

// ─────────────────────────────────────────────────────────────────────────────
// This example models a countdown timer with pause/resume support and an
// optional auto-pause feature that hooks into the platform lifecycle.
//
// States:
//   Idle             — waiting for the user to configure and start the timer
//   Running          — actively counting down; a keyed coroutine fires Tick every second
//   Paused           — count-down suspended; tracks whether a lifecycle event caused it
//   Finished         — timer reached zero
//
// The auto-pause flag is threaded through every state so it persists across
// pause/resume cycles and timer resets.
//
// Lifecycle integration is opt-in: the screen forwards ON_PAUSE / ON_RESUME
// events via Store.onLifecycleEvent, and the Running.onPause / Paused.onResume
// hooks react only when autoPause is true.
// ─────────────────────────────────────────────────────────────────────────────

class TimerStateMachine : StateMachine<TimerState, TimerAction, TimerEffect> by stateMachine(builder = {
    initialState(TimerState.Idle())

    // ── Idle ──────────────────────────────────────────────────────────────────

    state<TimerState.Idle> {
        on<TimerAction.SetDuration> {
            transition { state.copy(durationSeconds = action.seconds.coerceAtLeast(1)) }
        }
        on<TimerAction.SetAutoPause> {
            transition { state.toSelf(autoPause = action.enabled) }
        }
        on<TimerAction.Start> {
            transition {
                state.toRunning(
                    remainingSeconds = state.durationSeconds,
                    totalSeconds = state.durationSeconds,
                )
            }
        }
    }

    // ── Running ───────────────────────────────────────────────────────────────

    state<TimerState.Running> {
        // Start a coroutine that dispatches Tick every second. The keyed task("tick")
        // replaces any previous "tick" job when re-entering this state (e.g. after a
        // resume), and autoCancel = true cancels it automatically on the way out of
        // Running, so there is never more than one ticker active.
        onEnter {
            task(key = "tick", autoCancel = true) {
                while (true) {
                    delay(1_000)
                    dispatch(TimerAction.Tick)
                }
            }
        }

        on<TimerAction.Tick> {
            if (state.remainingSeconds <= 1) {
                transition { state.toFinished() }
                sideEffect(TimerEffect.Completed)
            } else {
                transition { state.copy(remainingSeconds = state.remainingSeconds - 1) }
            }
        }
        on<TimerAction.Pause> {
            transition { state.toPaused(pausedByLifecycle = false) }
        }
        on<TimerAction.PauseForLifecycle> {
            transition { state.toPaused(pausedByLifecycle = true) }
        }
        on<TimerAction.SetAutoPause> { transition { state.toSelf(autoPause = action.enabled) } }
        on<TimerAction.Reset> {
            transition { state.toIdle(durationSeconds = state.totalSeconds) }
        }

        // Auto-pause when the app goes to the background (if the user opted in).
        onPause {
            if (state.autoPause) dispatch(TimerAction.PauseForLifecycle)
        }
    }

    // ── Paused ────────────────────────────────────────────────────────────────

    state<TimerState.Paused> {
        on<TimerAction.Resume> {
            transition { state.toRunning() }
        }
        on<TimerAction.SetAutoPause> { transition { state.toSelf(autoPause = action.enabled) } }
        on<TimerAction.Reset> {
            transition { state.toIdle(durationSeconds = state.totalSeconds) }
        }

        // Auto-resume when the app returns to the foreground — only if this pause was
        // triggered by a lifecycle event, not by the user tapping Pause.
        onResume {
            if (state.pausedByLifecycle) dispatch(TimerAction.Resume)
        }
    }

    // ── Finished ──────────────────────────────────────────────────────────────

    state<TimerState.Finished> {
        on<TimerAction.Reset> {
            transition { state.toIdle(durationSeconds = state.totalSeconds) }
        }
    }

    install(LoggingPlugin(tag = "Timer"))
})
