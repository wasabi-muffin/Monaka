package tech.fika.monaka.processor.generator

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ksp.toClassName

/**
 * Rule D: generates `toSelf()` for objects and data objects, returning `this`.
 */
internal object ObjectSelfGenerator {

    fun generate(declaration: KSClassDeclaration): FunSpec =
        FunSpec.builder("toSelf")
            .receiver(declaration.toClassName())
            .returns(declaration.toClassName())
            .addStatement("return this")
            .build()
}
