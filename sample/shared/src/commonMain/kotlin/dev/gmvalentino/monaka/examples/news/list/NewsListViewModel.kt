package dev.gmvalentino.monaka.examples.news.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gmvalentino.monaka.runtime.StoreRegistry
import dev.gmvalentino.monaka.runtime.register
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class NewsListViewModel(
    registry: StoreRegistry,
    newsListStateMachine: NewsListStateMachine,
) : ViewModel() {
    val store = NewsListStore(
        stateMachine = newsListStateMachine,
        scope = viewModelScope,
    ).register(registry)
}
