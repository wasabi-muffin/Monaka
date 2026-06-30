package dev.gmvalentino.monaka.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.ksp.writeTo
import dev.gmvalentino.monaka.processor.generator.TargetTransitionGenerator

private const val TRANSITION_ANNOTATION = "dev.gmvalentino.monaka.core.Transition"

class TransitionProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {

    private val processed = mutableSetOf<String>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val annotated = resolver
            .getSymbolsWithAnnotation(TRANSITION_ANNOTATION)
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
                logger.error("TransitionProcessor failed on $qualifiedName: ${e.message}", declaration)
            }
        }

        return deferred
    }

    private fun processDeclaration(declaration: KSClassDeclaration) {
        val annotation = declaration.annotations.first {
            it.shortName.asString() == "Transition" &&
                it.annotationType.resolve().declaration.qualifiedName?.asString() == TRANSITION_ANNOTATION
        }

        @Suppress("UNCHECKED_CAST")
        val toTargets: List<KSClassDeclaration> = (
            annotation.arguments
                .firstOrNull { it.name?.asString() == "to" }
                ?.value as? List<KSType>
            )
            ?.mapNotNull { it.declaration as? KSClassDeclaration }
            ?: emptyList()

        if (declaration.typeParameters.isNotEmpty()) {
            logger.warn(
                "@Transition on generic types is not supported: ${declaration.qualifiedName?.asString()}",
                declaration,
            )
            return
        }

        val funSpecs = buildList {
            for (target in toTargets) {
                TargetTransitionGenerator.generate(
                    source = declaration,
                    target = target,
                    logger = logger,
                )?.let { add(it) }
            }
        }

        if (funSpecs.isEmpty()) return

        val packageName = declaration.packageName.asString()
        val fileName = (declaration.qualifiedName!!.asString())
            .removePrefix("$packageName.")
            .replace(".", "") + "Transitions"
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
