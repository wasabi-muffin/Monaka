package dev.gmvalentino.monaka.processor

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

class TransitionProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor = TransitionProcessor(
        codeGenerator = environment.codeGenerator,
        logger = environment.logger,
    )
}
