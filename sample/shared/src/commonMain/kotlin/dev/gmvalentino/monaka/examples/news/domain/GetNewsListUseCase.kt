package dev.gmvalentino.monaka.examples.news.domain

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

fun interface GetNewsListUseCase {
    suspend operator fun invoke(): List<News>
}

internal class MockGetNewsListUseCase : GetNewsListUseCase {
    override suspend fun invoke(): List<News> {
        delay(duration = 2.seconds)
        return listOf(
            News(id = 1, title = "Kotlin 2.3 Released with Improved Multiplatform Support", isRead = false),
            News(id = 2, title = "Jetpack Compose 1.8 Brings Lazy Layout Performance Fixes", isRead = true),
            News(id = 3, title = "Metro 1.1: Assisted Injection Now Fully Supported on iOS", isRead = false),
            News(id = 4, title = "Navigation 3 Hits Stable: Declarative Back Stacks for Compose", isRead = false),
            News(id = 5, title = "Coroutines 2.0 Preview: Structured Concurrency Enhancements", isRead = true),
        )
    }
}
