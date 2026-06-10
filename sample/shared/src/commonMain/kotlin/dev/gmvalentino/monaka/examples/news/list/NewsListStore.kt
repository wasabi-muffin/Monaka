package dev.gmvalentino.monaka.examples.news.list

import kotlinx.coroutines.CoroutineScope
import dev.gmvalentino.monaka.core.Store
import dev.gmvalentino.monaka.dsl.store
import dev.gmvalentino.monaka.plugin.Plugin

class NewsListStore(
    stateMachine: NewsListStateMachine,
    scope: CoroutineScope,
    plugins: List<Plugin<NewsListState, NewsListAction, NewsListEffect>> = emptyList()
) : Store<NewsListState, NewsListAction, NewsListEffect> by store(stateMachine = stateMachine, scope = scope, plugins = plugins)
