package tech.fika.monaka.core

import kotlin.reflect.KClass

/**
 * Marks a class or sealed hierarchy for automatic transition extension generation.
 *
 * When [to] is empty:
 *   - Sealed class/interface: generates `toSelf()` dispatching over all direct subclasses.
 *   - Data class: generates `toSelf()` as a `copy()` wrapper.
 *   - Object/data object: generates `toSelf()` returning `this`.
 *
 * When [to] is non-empty:
 *   - For each target `T`, generates `toT()` that constructs a `T` from the receiver.
 *   - Parameters with the same name and type as the receiver are given `this.prop` defaults;
 *     all other parameters are required.
 *
 * Both rules can coexist on the same element.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class Transition(vararg val to: KClass<out State> = [])
