package tech.fika.monaka.error

fun interface ErrorMapper {
    operator fun invoke(error: Throwable): AppError
}

/**
 * Default [ErrorMapper] used when none is installed on the machine.
 *
 * - If the throwable is already a [AppError], it is returned unchanged.
 * - Otherwise it is wrapped in [AppError.Unknown].
 */
object DefaultErrorMapper : ErrorMapper {
    override fun invoke(error: Throwable): AppError = error as? AppError ?: AppError.Unknown()
}

/**
 * Combine multiple [ErrorMapper]s into a single one using first-wins semantics.
 *
 * Each mapper is tried in order. The first result that is not [AppError.Unknown] is
 * returned immediately; remaining mappers are not called. If every mapper returns
 * [AppError.Unknown], the final [AppError.Unknown] is returned.
 *
 * ```kotlin
 * errorMapper(
 *     combineErrorMappers(
 *         NetworkErrorMapper,
 *         AuthErrorMapper,
 *     )
 * )
 * ```
 */
fun combineErrorMappers(vararg mappers: ErrorMapper): ErrorMapper = ErrorMapper { error ->
    mappers.forEach { mapper ->
        val result = mapper(error = error)
        if (result !is AppError.Unknown) return@ErrorMapper result
    }
    AppError.Unknown()
}
