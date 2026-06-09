package dev.gmvalentino.monaka.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.ksp.writeTo
import dev.gmvalentino.monaka.processor.generator.SealedSelfGenerator

private const val SELF_TRANSITION_ANNOTATION = "dev.gmvalentino.monaka.core.SelfTransition"

class SelfTransitionProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {

    private val processed = mutableSetOf<String>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val annotated = resolver
            .getSymbolsWithAnnotation(SELF_TRANSITION_ANNOTATION)
            .filterIsInstance<KSClassDeclaration>()

        val deferred = mutableListOf<KSAnnotated>()

        for (declaration in annotated) {
            if (!declaration.validate()) {
                deferred += declaration
                continue
            }
            val qualifiedName = declaration.qualifiedName?.asString() ?: continue
            if (qualifiedName in processed) continue

            try {
                processDeclaration(declaration)
                processed += qualifiedName
            } catch (e: Exception) {
                logger.error("SelfTransitionProcessor failed on $qualifiedName: ${e.message}", declaration)
            }
        }

        return deferred
    }

    private fun processDeclaration(declaration: KSClassDeclaration) {
        if (Modifier.SEALED !in declaration.modifiers) {
            logger.error(
                "@SelfTransition can only be applied to sealed classes or sealed interfaces, " +
                    "but ${declaration.qualifiedName?.asString()} is not sealed.",
                declaration,
            )
            return
        }

        if (declaration.typeParameters.isNotEmpty()) {
            logger.warn(
                "@SelfTransition on generic types is not supported: ${declaration.qualifiedName?.asString()}",
                declaration,
            )
            return
        }

        val funSpecs = SealedSelfGenerator.generate(declaration, logger)
        if (funSpecs.isEmpty()) return

        val packageName = declaration.packageName.asString()
        val fileName = "${declaration.simpleName.asString()}Transitions"
        val sourceFile = declaration.containingFile

        val fileSpec = FileSpec.builder(packageName, fileName)
            .apply { funSpecs.forEach { addFunction(it) } }
            .build()

        fileSpec.writeTo(
            codeGenerator,
            Dependencies(
                aggregating = false,
                *listOfNotNull(sourceFile).toTypedArray(),
            ),
        )
    }
}
