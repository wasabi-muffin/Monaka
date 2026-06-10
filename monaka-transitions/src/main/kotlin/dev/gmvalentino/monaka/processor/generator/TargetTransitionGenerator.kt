package dev.gmvalentino.monaka.processor.generator

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName

/**
 * Rule B: generates `toTarget()` from a source class to a target class.
 *
 * Target constructor params that match the source by name + resolved type get
 * `this.prop` defaults; all other params are required.
 */
internal object TargetTransitionGenerator {

    fun generate(
        source: KSClassDeclaration,
        target: KSClassDeclaration,
        logger: KSPLogger,
    ): FunSpec? {
        val functionName = buildFunctionName(source, target)

        // Self-referential transition: target is the same class as source.
        if (source.qualifiedName?.asString() == target.qualifiedName?.asString()) {
            return if (source.classKind == ClassKind.OBJECT) {
                FunSpec.builder(functionName)
                    .receiver(source.toClassName())
                    .returns(source.toClassName())
                    .addStatement("return this")
                    .build()
            } else {
                val constructor = source.primaryConstructor ?: run {
                    logger.error(
                        "@Transition self-target ${source.qualifiedName?.asString()} has no primary constructor",
                        source,
                    )
                    return null
                }
                val params = constructor.parameters.map { param ->
                    val name = param.name!!.asString()
                    ParameterSpec.builder(name, param.type.toTypeName())
                        .defaultValue("this.%N", name)
                        .build()
                }
                val argList = params.joinToString(",\n    ") { "${it.name} = ${it.name}" }
                FunSpec.builder(functionName)
                    .receiver(source.toClassName())
                    .returns(source.toClassName())
                    .apply { params.forEach { addParameter(it) } }
                    .addStatement("return this.copy(\n    $argList\n)")
                    .build()
            }
        }

        // Objects have no constructor — just return the singleton.
        if (target.classKind == ClassKind.OBJECT) {
            return FunSpec.builder(functionName)
                .receiver(source.toClassName())
                .returns(target.toClassName())
                .addStatement("return %T", target.toClassName())
                .build()
        }

        val constructor = target.primaryConstructor
        if (constructor == null) {
            logger.error(
                "@Transition target ${target.qualifiedName?.asString()} has no primary constructor",
                target,
            )
            return null
        }

        val sourceProps: Map<String, KSType> = source.collectAllProperties()

        val params = constructor.parameters.map { param ->
            val name = param.name!!.asString()
            val resolvedTargetType = param.type.resolve()
            val resolvedSourceType = sourceProps[name]

            val spec = ParameterSpec.builder(name, param.type.toTypeName())
            if (resolvedSourceType != null && resolvedSourceType == resolvedTargetType) {
                spec.defaultValue("this.%N", name)
            }
            spec.build()
        }

        val argList = params.joinToString(",\n    ") { "${it.name} = ${it.name}" }

        return FunSpec.builder(functionName)
            .receiver(source.toClassName())
            .returns(target.toClassName())
            .apply { params.forEach { addParameter(it) } }
            .addStatement("return %T(\n    $argList\n)", target.toClassName())
            .build()
    }

    /**
     * Builds the `toXxx` function name by computing the target's path relative to the
     * source's enclosing class, then stripping the common prefix.
     *
     * Examples:
     *   AppState.Loading → AppState.Auth.SigningIn  :  toAuthSigningIn  (cross-branch)
     *   AppState.Auth.SignedOut → AppState.Loading  :  toLoading         (up one level)
     *   AppState.Auth.SigningIn → AppState.Auth.SignedOut : toSignedOut  (same level)
     *   MyUiState.Loading → MyUiState.Stable        :  toStable          (flat, unchanged)
     */
    private fun buildFunctionName(source: KSClassDeclaration, target: KSClassDeclaration): String {
        val sourceParents = source.enclosingChain()          // e.g. ["AppState"] for AppState.Loading
        val targetChain = target.enclosingChain() + target.simpleName.asString() // e.g. ["AppState","Auth","SigningIn"]
        val prefixLen = sourceParents.zip(targetChain).takeWhile { (a, b) -> a == b }.size
        val nameParts = targetChain.drop(prefixLen)
        return "to" + nameParts.joinToString("") { it.replaceFirstChar { c -> c.uppercase() } }
    }

    /** Returns the chain of enclosing class simple-names from outermost to innermost, excluding self. */
    private fun KSClassDeclaration.enclosingChain(): List<String> {
        val chain = mutableListOf<String>()
        var current = parentDeclaration as? KSClassDeclaration
        while (current != null) {
            chain.add(0, current.simpleName.asString())
            current = current.parentDeclaration as? KSClassDeclaration
        }
        return chain
    }

    // Walks declared properties of this class and its super types (one pass each),
    // preferring the most-derived declaration for duplicate names.
    private fun KSClassDeclaration.collectAllProperties(): Map<String, KSType> {
        val result = linkedMapOf<String, KSType>()
        fun collect(decl: KSClassDeclaration) {
            decl.getDeclaredProperties().forEach { prop ->
                result.putIfAbsent(prop.simpleName.asString(), prop.type.resolve())
            }
            decl.superTypes
                .mapNotNull { it.resolve().declaration as? KSClassDeclaration }
                .forEach { collect(it) }
        }
        collect(this)
        return result
    }
}
