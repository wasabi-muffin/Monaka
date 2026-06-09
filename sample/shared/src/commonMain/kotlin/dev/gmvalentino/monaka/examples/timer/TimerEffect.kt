package dev.gmvalentino.monaka.examples.timer

import dev.gmvalentino.monaka.core.Effect

sealed interface TimerEffect : Effect {
    data object Completed : TimerEffect
}
