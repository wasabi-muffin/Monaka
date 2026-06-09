package dev.gmvalentino.monaka.examples.timer

import dev.gmvalentino.monaka.core.Action

sealed interface TimerAction : Action {
    data class SetDuration(val seconds: Int) : TimerAction
    data class SetAutoPause(val enabled: Boolean) : TimerAction
    data object Start : TimerAction
    data object Tick : TimerAction
    data object Pause : TimerAction

    /** Internal action dispatched by the onPause lifecycle hook — auto-resumes on onResume. */
    data object PauseForLifecycle : TimerAction
    data object Resume : TimerAction
    data object Reset : TimerAction
}
