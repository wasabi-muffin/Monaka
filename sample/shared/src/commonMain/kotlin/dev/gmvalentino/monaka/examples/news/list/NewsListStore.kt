package dev.gmvalentino.monaka.examples.news.list

import dev.gmvalentino.monaka.core.Store
import dev.gmvalentino.monaka.dsl.store
import dev.gmvalentino.monaka.plugin.Plugin
import kotlinx.coroutines.CoroutineScope

class NewsListStore(
    stateMachine: NewsListStateMachine,
    scope: CoroutineScope,
    plugins: List<Plugin> = emptyList(),
) : Store<NewsListState, NewsListAction, NewsListEffect> by store(
    stateMachine = stateMachine,
    scope = scope,
    plugins = plugins,
)
