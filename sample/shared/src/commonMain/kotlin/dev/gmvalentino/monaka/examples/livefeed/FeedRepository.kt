package dev.gmvalentino.monaka.examples.livefeed

import dev.gmvalentino.monaka.ext.nowMs
import kotlinx.coroutines.delay

interface FeedRepository {
    suspend fun search(query: String): List<FeedItem>
    suspend fun fetchSince(query: String, afterTimestamp: Long): List<FeedItem>
}

internal class FakeFeedRepository : FeedRepository {
    private val items = run {
        val now = nowMs()
        listOf(
            FeedItem("1", "Kotlin 2.2 ships with multi-dollar string templates", now - 3_600_000),
            FeedItem("2", "Jetpack Compose 2.0 reaches stable release", now - 7_200_000),
            FeedItem("3", "KMP officially supports all targets as stable", now - 10_800_000),
            FeedItem("4", "Android Studio Narwhal adds AI pair programming", now - 14_400_000),
            FeedItem("5", "Coroutines 2.0 introduces structured parallelism", now - 18_000_000),
            FeedItem("6", "Ktor 4.0 brings major performance improvements", now - 21_600_000),
            FeedItem("7", "Arrow 2.1 adds new functional programming utilities", now - 25_200_000),
            FeedItem("8", "Google I/O 2026: AI-first Android development", now - 28_800_000),
        )
    }

    override suspend fun search(query: String): List<FeedItem> {
        delay(800)
        if (query.lowercase() == "error") throw Exception("Simulated network error — tap Retry to recover")
        return items.filter { it.title.contains(query, ignoreCase = true) }
    }

    override suspend fun fetchSince(query: String, afterTimestamp: Long): List<FeedItem> {
        delay(300)
        return emptyList()
    }
}
