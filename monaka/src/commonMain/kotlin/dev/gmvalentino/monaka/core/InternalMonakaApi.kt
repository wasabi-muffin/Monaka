package dev.gmvalentino.monaka.core

/**
 * Marks an API as internal to the Monaka library — intended for use by `:monaka-test` only.
 *
 * Such APIs may change or be removed without notice and must not be used in production code.
 * Calling them without an explicit `@OptIn(InternalMonakaApi::class)` is a compile-time error.
 */
@RequiresOptIn(
    message = "This is an internal Monaka API intended for testing infrastructure only. " +
              "It may change or be removed without notice. Do not use in production code.",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
public annotation class InternalMonakaApi
