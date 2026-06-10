package dev.gmvalentino.monaka.examples.news.domain

fun interface MarkAllNewsAsReadUseCase {
    suspend operator fun invoke()
}

internal class MockMarkAllNewsAsReadUseCase : MarkAllNewsAsReadUseCase {
    override suspend fun invoke() = Unit
}
