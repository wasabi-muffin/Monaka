package dev.gmvalentino.monaka.examples.timer

import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import dev.gmvalentino.monaka.core.LifecycleEvent
import dev.gmvalentino.monaka.core.StateHook
import dev.gmvalentino.monaka.test.testStore

class TimerStateMachineTest {

    @Test
    fun startTransitionsToRunning() = testStore(machine = TimerStateMachine()) {
        testCase("Start from Idle begins the countdown") {
            trigger(TimerAction.Start) {
                expectState<TimerState.Running> {
                    state.remainingSeconds == 60 && state.totalSeconds == 60
                }
            }
        }
    }

    @Test
    fun setDurationBeforeStart() = testStore(machine = TimerStateMachine()) {
        testCase("SetDuration changes the configured time") {
            trigger(TimerAction.SetDuration(seconds = 30)) {
                expectState<TimerState.Idle> { state.durationSeconds == 30 }
            }
            trigger(TimerAction.Start) {
                expectState<TimerState.Running> {
                    state.remainingSeconds == 30 && state.totalSeconds == 30
                }
            }
        }
    }

    @Test
    fun pauseAndResume() = testStore(machine = TimerStateMachine()) {
        testCase("Pause suspends the timer; Resume restarts it") {
            given(TimerState.Running(remainingSeconds = 45, totalSeconds = 60, autoPause = false))

            trigger(TimerAction.Pause) {
                expectState<TimerState.Paused> {
                    state.remainingSeconds == 45 && !state.pausedByLifecycle
                }
            }
            trigger(TimerAction.Resume) {
                expectState<TimerState.Running> { state.remainingSeconds == 45 }
            }
        }
    }

    @Test
    fun resetFromRunningReturnsToIdle() = testStore(machine = TimerStateMachine()) {
        testCase("Reset from Running goes back to Idle with original duration") {
            given(TimerState.Running(remainingSeconds = 20, totalSeconds = 60, autoPause = false))

            trigger(TimerAction.Reset) {
                expectState<TimerState.Idle> { state.durationSeconds == 60 }
            }
        }
    }

    @Test
    fun resetFromFinishedReturnsToIdle() = testStore(machine = TimerStateMachine()) {
        testCase("Reset from Finished goes back to Idle") {
            given(TimerState.Finished(totalSeconds = 60, autoPause = false))

            trigger(TimerAction.Reset) {
                expectState<TimerState.Idle> { state.durationSeconds == 60 }
            }
        }
    }

    @Test
    fun tickDecrementsRemainingSeconds() = testStore(machine = TimerStateMachine()) {
        testCase("Tick decrements remaining seconds by one") {
            given(TimerState.Running(remainingSeconds = 10, totalSeconds = 60, autoPause = false))

            trigger(TimerAction.Tick) {
                expectState<TimerState.Running> { state.remainingSeconds == 9 }
            }
        }
    }

    @Test
    fun lastTickTransitionsToFinishedWithCompletedEffect() = testStore(machine = TimerStateMachine()) {
        testCase("Tick at remainingSeconds=1 finishes the timer") {
            given(TimerState.Running(remainingSeconds = 1, totalSeconds = 60, autoPause = false))

            trigger(TimerAction.Tick) {
                expectState<TimerState.Finished> { state.totalSeconds == 60 }
                expectEffect(TimerEffect.Completed)
            }
        }
    }

    @Test
    fun autoPauseOnLifecyclePause() = testStore(machine = TimerStateMachine()) {
        testCase("lifecycle pause auto-pauses when autoPause is enabled") {
            given(TimerState.Running(remainingSeconds = 30, totalSeconds = 60, autoPause = true))

            trigger(LifecycleEvent.OnPause) {
                expectAction(TimerAction.PauseForLifecycle)
                expectState<TimerState.Paused> { state.pausedByLifecycle }
            }
        }
    }

    @Test
    fun lifecyclePauseNoopWhenAutoPauseDisabled() = testStore(machine = TimerStateMachine()) {
        testCase("lifecycle pause is ignored when autoPause is disabled") {
            given(TimerState.Running(remainingSeconds = 30, totalSeconds = 60, autoPause = false))

            trigger(LifecycleEvent.OnPause) {
                expectNoAction()
                expectNoEffects()
            }
        }
    }

    @Test
    fun autoPauseLifecycleResumesOnForeground() = testStore(machine = TimerStateMachine()) {
        testCase("lifecycle resume auto-resumes when pausedByLifecycle is true") {
            given(
                TimerState.Paused(
                    remainingSeconds = 30,
                    totalSeconds = 60,
                    autoPause = true,
                    pausedByLifecycle = true,
                )
            )

            trigger(LifecycleEvent.OnResume) {
                expectAction(TimerAction.Resume)
                expectState<TimerState.Running> { state.remainingSeconds == 30 }
            }
        }
    }

    @Test
    fun manualPauseDoesNotAutoResumeOnForeground() = testStore(machine = TimerStateMachine()) {
        testCase("lifecycle resume does nothing when paused manually") {
            given(
                TimerState.Paused(
                    remainingSeconds = 30,
                    totalSeconds = 60,
                    autoPause = true,
                    pausedByLifecycle = false,
                )
            )

            trigger(LifecycleEvent.OnResume) {
                expectNoAction()
            }
        }
    }

    @Test
    fun tickerDispatchesEverySecondUntilFinished() = testStore(machine = TimerStateMachine()) {
        testCase("ticker decrements remaining seconds each second and finishes at zero") {
            trigger(TimerAction.SetDuration(seconds = 3)) {
                expectState<TimerState.Idle> { state.durationSeconds == 3 }
            }
            trigger(TimerAction.Start) {
                expectState<TimerState.Running> { state.remainingSeconds == 3 }
            }

            advanceTime(1.seconds) {
                expectAction<TimerAction.Tick>()
                expectState<TimerState.Running> { state.remainingSeconds == 2 }
            }

            advanceTime(1.seconds) {
                expectAction<TimerAction.Tick>()
                expectState<TimerState.Running> { state.remainingSeconds == 1 }
            }

            advanceTime(1.seconds) {
                expectAction<TimerAction.Tick>()
                expectState<TimerState.Finished> { state.totalSeconds == 3 }
                expectEffect(TimerEffect.Completed)
            }
        }
    }

    @Test
    fun tickerStopsOnPauseAndResumesOnResume() = testStore(machine = TimerStateMachine()) {
        testCase("ticker stops when paused and restarts when resumed") {
            given(TimerState.Running(remainingSeconds = 10, totalSeconds = 60, autoPause = true))

            trigger(StateHook.OnEnter)

            advanceTime(1.seconds) {
                expectAction<TimerAction.Tick>()
                expectState<TimerState.Running> { state.remainingSeconds == 9 }
            }

            trigger(TimerAction.Pause) {
                expectState<TimerState.Paused> { state.remainingSeconds == 9 }
            }

            // No Tick should fire while paused
            advanceTime(3.seconds) {
                expectNoAction()
            }

            trigger(TimerAction.Resume) {
                expectState<TimerState.Running> { state.remainingSeconds == 9 }
            }

            advanceTime(1.seconds) {
                expectAction<TimerAction.Tick>()
                expectState<TimerState.Running> { state.remainingSeconds == 8 }
            }
        }
    }

    @Test
    fun onEnterStartsTickerInRunningState() = testStore(machine = TimerStateMachine()) {
        testCase("OnEnter fires the onEnter hook for Running, starting the tick coroutine") {
            given(TimerState.Running(remainingSeconds = 5, totalSeconds = 5, autoPause = false))

            trigger(StateHook.OnEnter) {}

            advanceTime(1.seconds) {
                expectAction<TimerAction.Tick>()
                expectState<TimerState.Running> { state.remainingSeconds == 4 }
            }
        }
    }

    @Test
    fun leavingRunningCancelsTicker() = testStore(machine = TimerStateMachine()) {
        testCase("autoCancel cancels the tick coroutine when Running transitions to Paused") {
            given(TimerState.Running(remainingSeconds = 5, totalSeconds = 5, autoPause = false))

            // Start the ticker via OnEnter, then leave Running via a Pause action.
            // The keyed task("tick", autoCancel = true) must be cancelled by the runtime
            // on the state-type change so no further Tick actions reach the queue.
            trigger(StateHook.OnEnter)
            trigger(TimerAction.Pause) {
                expectState<TimerState.Paused>()
            }

            advanceTime(3.seconds) {
                expectNoAction()
            }
        }
    }

    @Test
    fun autoPauseFlagPersistedAcrossTransitions() = testStore(machine = TimerStateMachine()) {
        testCase("autoPause flag persists through start, pause, resume") {
            trigger(TimerAction.SetAutoPause(enabled = true)) {
                expectState<TimerState.Idle> { state.autoPause }
            }
            trigger(TimerAction.Start) {
                expectState<TimerState.Running> { state.autoPause }
            }
            trigger(TimerAction.Pause) {
                expectState<TimerState.Paused> { state.autoPause }
            }
            trigger(TimerAction.Resume) {
                expectState<TimerState.Running> { state.autoPause }
            }
        }
    }
}
