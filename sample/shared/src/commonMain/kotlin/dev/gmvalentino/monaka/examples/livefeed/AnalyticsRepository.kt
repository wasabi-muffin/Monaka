package dev.gmvalentino.monaka.examples.livefeed

interface AnalyticsRepository {
    suspend fun trackSearch(query: String, resultCount: Int)
    suspend fun trackItemViewed(itemId: String)
}

internal class FakeAnalyticsRepository : AnalyticsRepository {
    override suspend fun trackSearch(query: String, resultCount: Int) = Unit
    override suspend fun trackItemViewed(itemId: String) = Unit
}
