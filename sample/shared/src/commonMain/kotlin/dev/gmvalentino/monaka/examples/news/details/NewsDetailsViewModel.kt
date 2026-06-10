package dev.gmvalentino.monaka.examples.news.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gmvalentino.monaka.runtime.StoreRegistry
import dev.gmvalentino.monaka.runtime.register
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey

@AssistedInject
class NewsDetailsViewModel(
    @Assisted val id: Int,
    registry: StoreRegistry,
    newsDetailsStateMachine: NewsDetailsStateMachine,
) : ViewModel() {
    val store = NewsDetailsStore(
        stateMachine = newsDetailsStateMachine,
        scope = viewModelScope,
        initialState = NewsDetailsState.Initial(id = id),
    ).register(registry)

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : ManualViewModelAssistedFactory {
        fun create(@Assisted id: Int): NewsDetailsViewModel
    }
}
