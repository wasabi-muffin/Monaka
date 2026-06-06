package tech.fika.monaka.processor

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

class SelfTransitionProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        SelfTransitionProcessor(
            codeGenerator = environment.codeGenerator,
            logger = environment.logger,
        )
}
