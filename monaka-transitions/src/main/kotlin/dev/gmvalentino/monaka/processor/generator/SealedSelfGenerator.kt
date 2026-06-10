package dev.gmvalentino.monaka.processor.generator

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName

/**
 * Rule A: generates `toSelf()` for sealed classes/interfaces.
 *
 * Parameters = properties declared on the sealed type that also appear in constructor params
 * (filters out computed properties). For sealed interfaces without a primary constructor,
 * all declared properties are included.
 *
 * Each when-branch fills shared params from the function args and keeps subclass-specific
 * properties unchanged via `this.prop`.
 */
internal object SealedSelfGenerator {

    fun generate(declaration: KSClassDeclaration, logger: KSPLogger): List<FunSpec> {
        val subclasses = declaration.getSealedSubclasses().toList()
        if (subclasses.isEmpty()) {
            logger.warn(
                "@Transition on sealed type with no direct subclasses: ${declaration.qualifiedName?.asString()}",
                declaration,
            )
            return emptyList()
        }

        val constructorParamNames: Set<String>? = declaration.primaryConstructor
            ?.parameters
            ?.mapNotNull { it.name?.asString() }
            ?.toSet()

        val sharedProps: List<KSPropertyDeclaration> = declaration.getDeclaredProperties()
            .filter { prop ->
                constructorParamNames == null || prop.simpleName.asString() in constructorParamNames
            }
            .toList()

        val sharedPropNames: Set<String> = sharedProps.map { it.simpleName.asString() }.toSet()

        val funBuilder = FunSpec.builder("toSelf")
            .receiver(declaration.toClassName())
            .returns(declaration.toClassName())

        for (prop in sharedProps) {
            funBuilder.addParameter(
                ParameterSpec.builder(prop.simpleName.asString(), prop.type.toTypeName())
                    .defaultValue("this.%N", prop.simpleName.asString())
                    .build(),
            )
        }

        funBuilder.beginControlFlow("return when (this)")

        for (sub in subclasses) {
            addWhenBranch(funBuilder, sub, sharedProps, sharedPropNames, logger)
        }

        funBuilder.endControlFlow()

        return listOf(funBuilder.build())
    }

    private fun addWhenBranch(
        builder: FunSpec.Builder,
        sub: KSClassDeclaration,
        sharedProps: List<KSPropertyDeclaration>,
        sharedPropNames: Set<String>,
        logger: KSPLogger,
    ) {
        when {
            sub.classKind == ClassKind.OBJECT -> {
                builder.addStatement("is %T -> %T", sub.toClassName(), sub.toClassName())
            }

            Modifier.SEALED in sub.modifiers -> {
                for (nested in sub.getSealedSubclasses()) {
                    addWhenBranch(builder, nested, sharedProps, sharedPropNames, logger)
                }
            }

            Modifier.DATA in sub.modifiers -> {
                buildBranch(builder, sub, sharedProps, sharedPropNames, useConstructor = false)
            }

            else -> {
                buildBranch(builder, sub, sharedProps, sharedPropNames, useConstructor = true)
            }
        }
    }

    private fun buildBranch(
        builder: FunSpec.Builder,
        sub: KSClassDeclaration,
        sharedProps: List<KSPropertyDeclaration>,
        sharedPropNames: Set<String>,
        useConstructor: Boolean,
    ) {
        val subConstructorParamNames: Set<String> = sub.primaryConstructor
            ?.parameters
            ?.mapNotNull { it.name?.asString() }
            ?.toSet()
            ?: emptySet()

        val ownProps: List<KSPropertyDeclaration> = sub.getDeclaredProperties()
            .filter { prop ->
                val name = prop.simpleName.asString()
                name !in sharedPropNames &&
                    (subConstructorParamNames.isEmpty() || name in subConstructorParamNames)
            }
            .toList()

        val sharedArgs = sharedProps.joinToString(", ") { prop ->
            val n = prop.simpleName.asString(); "$n = $n"
        }
        val ownArgs = ownProps.joinToString(", ") { prop ->
            val n = prop.simpleName.asString(); "$n = this.$n"
        }
        val allArgs = listOfNotNull(sharedArgs.ifEmpty { null }, ownArgs.ifEmpty { null })
            .joinToString(", ")

        if (useConstructor) {
            builder.addStatement("is %T -> %T($allArgs)", sub.toClassName(), sub.toClassName())
        } else {
            builder.addStatement("is %T -> copy($allArgs)", sub.toClassName())
        }
    }
}
