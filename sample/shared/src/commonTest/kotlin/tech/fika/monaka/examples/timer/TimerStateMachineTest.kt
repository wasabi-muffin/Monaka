package tech.fika.monaka.examples.timer

import kotlin.test.Test
import tech.fika.monaka.core.LifecycleEvent
import tech.fika.monaka.test.testStore

class TimerStateMachineTest {

    @Test
    fun startTransitionsToRunning() = testStore(machine = TimerStateMachine()) {
        scenario("Start from Idle begins the countdown") {
            trigger(TimerAction.Start) {
                expectState<TimerState.Running> {
                    it.remainingSeconds == 60 && it.totalSeconds == 60
                }
            }
        }
    }

    @Test
    fun setDurationBeforeStart() = testStore(machine = TimerStateMachine()) {
        scenario("SetDuration changes the configured time") {
            trigger(TimerAction.SetDuration(seconds = 30)) {
                expectState<TimerState.Idle> { it.durationSeconds == 30 }
            }
            trigger(TimerAction.Start) {
                expectState<TimerState.Running> {
                    it.remainingSeconds == 30 && it.totalSeconds == 30
                }
            }
        }
    }

    @Test
    fun pauseAndResume() = testStore(machine = TimerStateMachine()) {
        scenario("Pause suspends the timer; Resume restarts it") {
            given(TimerState.Running(remainingSeconds = 45, totalSeconds = 60, autoPause = false))

            trigger(TimerAction.Pause) {
                expectState<TimerState.Paused> {
                    it.remainingSeconds == 45 && !it.pausedByLifecycle
                }
            }
            trigger(TimerAction.Resume) {
                expectState<TimerState.Running> { it.remainingSeconds == 45 }
            }
        }
    }

    @Test
    fun resetFromRunningReturnsToIdle() = testStore(machine = TimerStateMachine()) {
        scenario("Reset from Running goes back to Idle with original duration") {
            given(TimerState.Running(remainingSeconds = 20, totalSeconds = 60, autoPause = false))

            trigger(TimerAction.Reset) {
                expectState<TimerState.Idle> { it.durationSeconds == 60 }
            }
        }
    }

    @Test
    fun resetFromFinishedReturnsToIdle() = testStore(machine = TimerStateMachine()) {
        scenario("Reset from Finished goes back to Idle") {
            given(TimerState.Finished(totalSeconds = 60, autoPause = false))

            trigger(TimerAction.Reset) {
                expectState<TimerState.Idle> { it.durationSeconds == 60 }
            }
        }
    }

    @Test
    fun tickDecrementsRemainingSeconds() = testStore(machine = TimerStateMachine()) {
        scenario("Tick decrements remaining seconds by one") {
            given(TimerState.Running(remainingSeconds = 10, totalSeconds = 60, autoPause = false))

            trigger(TimerAction.Tick) {
                expectState<TimerState.Running> { it.remainingSeconds == 9 }
            }
        }
    }

    @Test
    fun lastTickTransitionsToFinishedWithCompletedEffect() = testStore(machine = TimerStateMachine()) {
        scenario("Tick at remainingSeconds=1 finishes the timer") {
            given(TimerState.Running(remainingSeconds = 1, totalSeconds = 60, autoPause = false))

            trigger(TimerAction.Tick) {
                expectState<TimerState.Finished> { it.totalSeconds == 60 }
                expectEffect(TimerEffect.Completed)
            }
        }
    }

    @Test
    fun autoPauseOnLifecyclePause() = testStore(machine = TimerStateMachine()) {
        scenario("lifecycle pause auto-pauses when autoPause is enabled") {
            given(TimerState.Running(remainingSeconds = 30, totalSeconds = 60, autoPause = true))

            trigger(LifecycleEvent.OnPause) {
                expectAction(TimerAction.PauseForLifecycle)
                expectState<TimerState.Paused> { it.pausedByLifecycle }
            }
        }
    }

    @Test
    fun lifecyclePauseNoopWhenAutoPauseDisabled() = testStore(machine = TimerStateMachine()) {
        scenario("lifecycle pause is ignored when autoPause is disabled") {
            given(TimerState.Running(remainingSeconds = 30, totalSeconds = 60, autoPause = false))

            trigger(LifecycleEvent.OnPause) {
                expectNoAction()
                expectNoEffects()
            }
        }
    }

    @Test
    fun autoPauseLifecycleResumesOnForeground() = testStore(machine = TimerStateMachine()) {
        scenario("lifecycle resume auto-resumes when pausedByLifecycle is true") {
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
                expectState<TimerState.Running> { it.remainingSeconds == 30 }
            }
        }
    }

    @Test
    fun manualPauseDoesNotAutoResumeOnForeground() = testStore(machine = TimerStateMachine()) {
        scenario("lifecycle resume does nothing when paused manually") {
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
    fun autoPauseFlagPersistedAcrossTransitions() = testStore(machine = TimerStateMachine()) {
        scenario("autoPause flag persists through start, pause, resume") {
            trigger(TimerAction.SetAutoPause(enabled = true)) {
                expectState<TimerState.Idle> { it.autoPause }
            }
            trigger(TimerAction.Start) {
                expectState<TimerState.Running> { it.autoPause }
            }
            trigger(TimerAction.Pause) {
                expectState<TimerState.Paused> { it.autoPause }
            }
            trigger(TimerAction.Resume) {
                expectState<TimerState.Running> { it.autoPause }
            }
        }
    }
}
