package dev.gmvalentino.monaka.examples.news.domain

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

fun interface GetNewsDetailsUseCase {
    suspend operator fun invoke(id: Int): NewsDetails
}

internal class MockGetNewsDetailsUseCase : GetNewsDetailsUseCase {
    override suspend fun invoke(id: Int): NewsDetails {
        delay(duration = 2.seconds)
        return when (id) {
            1 -> NewsDetails(
                id = 1,
                title = "Kotlin 2.3 Released with Improved Multiplatform Support",
                body = "The Kotlin team has released version 2.3, bringing a raft of improvements to Kotlin Multiplatform. " +
                        "Highlights include faster compilation on all targets, improved type inference for generic lambdas, " +
                        "and a new @ExperimentalStdlibApi graduation for several APIs that have been in preview. " +
                        "The release also closes several long-standing issues in the K2 compiler pipeline, " +
                        "reducing the gap between JVM and Native behavior in edge cases.",
            )

            2 -> NewsDetails(
                id = 2,
                title = "Jetpack Compose 1.8 Brings Lazy Layout Performance Fixes",
                body = "Jetpack Compose 1.8 ships a focused performance pass on LazyColumn and LazyRow. " +
                        "Measuring benchmarks on a Pixel 8 show a 22 % reduction in frame time during fast flings " +
                        "for lists with complex item composables. The release also improves stability tracking for " +
                        "data classes that implement custom equals, reducing unnecessary recompositions in " +
                        "production apps that rely heavily on domain model collections.",
            )

            3 -> NewsDetails(
                id = 3,
                title = "Metro 1.1: Assisted Injection Now Fully Supported on iOS",
                body = "Metro 1.1 lands full support for @AssistedInject and ManualViewModelAssistedFactory " +
                        "on Kotlin/Native targets, including iosArm64 and iosSimulatorArm64. " +
                        "Previously, assisted ViewModel injection required workarounds on iOS due to limitations " +
                        "in the compiler plugin. The new release resolves these by moving factory code generation " +
                        "entirely into the IR backend, making the DI graph truly multiplatform.",
            )

            4 -> NewsDetails(
                id = 4,
                title = "Navigation 3 Hits Stable: Declarative Back Stacks for Compose",
                body = "After several alpha and beta cycles, Navigation 3 has reached its first stable release. " +
                        "The library replaces the NavController/NavHost model with a simple NavBackStack<T> " +
                        "backed by a plain MutableList. Developers own the back stack entirely — " +
                        "push by adding a key, pop by removing it. The entryProvider DSL wires each key " +
                        "type to a Composable destination, keeping navigation logic colocated with the UI.",
            )

            5 -> NewsDetails(
                id = 5,
                title = "Coroutines 2.0 Preview: Structured Concurrency Enhancements",
                body = "The first developer preview of kotlinx.coroutines 2.0 introduces structured concurrency " +
                        "improvements aimed at making cancellation and exception propagation more predictable. " +
                        "Key additions include a new supervisorScope overload that accepts a CoroutineExceptionHandler, " +
                        "a Flow.chunked operator, and deprecated usage of GlobalScope in the standard library itself. " +
                        "The team is targeting a stable release later this year, with migration guides already published.",
            )

            else -> throw IllegalStateException("News with id $id could not be found")
        }
    }
}
