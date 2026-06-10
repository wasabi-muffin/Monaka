package dev.gmvalentino.monaka.examples.news.list

import dev.gmvalentino.monaka.core.Effect

sealed interface NewsListEffect : Effect {
    data class NavigateNewsDetails(val id: Int) : NewsListEffect
}
