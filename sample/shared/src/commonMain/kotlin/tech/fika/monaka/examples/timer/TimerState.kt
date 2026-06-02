package tech.fika.monaka.examples.timer

import tech.fika.monaka.core.State

sealed interface TimerState : State {
    val autoPause: Boolean

    data class Idle(
        val durationSeconds: Int = 60,
        override val autoPause: Boolean = false,
    ) : TimerState

    data class Running(
        val remainingSeconds: Int,
        val totalSeconds: Int,
        override val autoPause: Boolean,
    ) : TimerState {
        val progress: Float get() = remainingSeconds.toFloat() / totalSeconds.toFloat()
    }

    data class Paused(
        val remainingSeconds: Int,
        val totalSeconds: Int,
        override val autoPause: Boolean,
        /** True when this pause was triggered by a lifecycle ON_PAUSE event — auto-resumes on ON_RESUME. */
        val pausedByLifecycle: Boolean = false,
    ) : TimerState {
        val progress: Float get() = remainingSeconds.toFloat() / totalSeconds.toFloat()
    }

    data class Finished(
        val totalSeconds: Int,
        override val autoPause: Boolean,
    ) : TimerState
}
