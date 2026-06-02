package tech.fika.monaka.examples.timer

import tech.fika.monaka.core.Effect

sealed interface TimerEffect : Effect {
    data object Completed : TimerEffect
}
