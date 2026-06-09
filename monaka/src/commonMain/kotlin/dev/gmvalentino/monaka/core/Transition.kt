package dev.gmvalentino.monaka.core

import kotlin.reflect.KClass

/**
 * Generates cross-state transition extensions for a class.
 *
 * For each target `T` in [to], generates a `toT()` function that constructs a `T` from the
 * receiver. Parameters with the same name and type as the receiver get `this.prop` defaults;
 * all other parameters are required.
 *
 * For `toSelf()` on sealed types, use [@SelfTransition].
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
public annotation class Transition(vararg val to: KClass<out State> = [])
