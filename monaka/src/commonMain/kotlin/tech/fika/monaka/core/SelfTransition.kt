package tech.fika.monaka.core

/**
 * Generates a `toSelf()` extension for a sealed class or sealed interface.
 *
 * The generated function accepts the sealed type's shared properties as parameters
 * (each defaulting to `this.prop`) and returns a new instance with those values
 * applied via a `when` dispatch over all direct subclasses.
 *
 * Restricted to sealed types. Applying this to a non-sealed class or object is a
 * compile-time error reported by the KSP processor.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class SelfTransition
