package tech.fika.monaka.processor.generator

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName

/**
 * Rule C: generates `toSelf()` for plain data classes as a `copy()` wrapper.
 * All params get `this.prop` defaults.
 */
internal object DataClassSelfGenerator {

    fun generate(declaration: KSClassDeclaration): FunSpec {
        val params = declaration.primaryConstructor?.parameters.orEmpty()

        val paramSpecs = params.map { param ->
            val name = param.name!!.asString()
            ParameterSpec.builder(name, param.type.toTypeName())
                .defaultValue("this.%N", name)
                .build()
        }

        val argList = paramSpecs.joinToString(", ") { "${it.name} = ${it.name}" }

        return FunSpec.builder("toSelf")
            .receiver(declaration.toClassName())
            .returns(declaration.toClassName())
            .apply { paramSpecs.forEach { addParameter(it) } }
            .addStatement("return copy($argList)")
            .build()
    }
}
