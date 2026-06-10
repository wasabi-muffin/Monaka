package dev.gmvalentino.monaka.examples.news

import dev.gmvalentino.monaka.examples.news.details.NewsDetailsRelay
import dev.gmvalentino.monaka.examples.news.details.NewsDetailsStateMachine
import dev.gmvalentino.monaka.examples.news.domain.GetNewsDetailsUseCase
import dev.gmvalentino.monaka.examples.news.domain.GetNewsListUseCase
import dev.gmvalentino.monaka.examples.news.domain.MarkAllNewsAsReadUseCase
import dev.gmvalentino.monaka.examples.news.domain.MockGetNewsDetailsUseCase
import dev.gmvalentino.monaka.examples.news.domain.MockGetNewsListUseCase
import dev.gmvalentino.monaka.examples.news.domain.MockMarkAllNewsAsReadUseCase
import dev.gmvalentino.monaka.examples.news.list.NewsListStateMachine
import dev.gmvalentino.monaka.runtime.StoreRegistry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metrox.viewmodel.ViewModelGraph

@DependencyGraph(AppScope::class)
interface NewsGraph : ViewModelGraph {

    @Provides
    @SingleIn(AppScope::class)
    fun provideStoreRegistry(): StoreRegistry = StoreRegistry {
        +NewsDetailsRelay
    }

    @Provides
    fun provideGetNewsListUseCase(): GetNewsListUseCase = MockGetNewsListUseCase()

    @Provides
    fun provideMarkAllNewsAsReadUseCase(): MarkAllNewsAsReadUseCase = MockMarkAllNewsAsReadUseCase()

    @Provides
    fun provideGetNewsDetailsUseCase(): GetNewsDetailsUseCase = MockGetNewsDetailsUseCase()

    @Provides
    fun provideNewsListStateMachine(
        getNewsListUseCase: GetNewsListUseCase,
        markAllNewsAsReadUseCase: MarkAllNewsAsReadUseCase,
    ): NewsListStateMachine = NewsListStateMachine(getNewsListUseCase, markAllNewsAsReadUseCase)

    @Provides
    fun provideNewsDetailsStateMachine(
        getNewsDetailsUseCase: GetNewsDetailsUseCase,
    ): NewsDetailsStateMachine = NewsDetailsStateMachine(getNewsDetailsUseCase)
}
