package tech.fika.monaka.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.*
import tech.fika.monaka.gradle.emit.PumlEmitter
import tech.fika.monaka.gradle.parser.KtSourceParser

@CacheableTask
abstract class GenerateStateMachinePumlTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:SkipWhenEmpty
    abstract val sources: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val out = outputDir.get().asFile.also { it.mkdirs() }
        val emitter = PumlEmitter()
        val models = KtSourceParser().parseFiles(sources.asFileTree.files)

        if (models.isEmpty()) {
            logger.lifecycle("Monaka: no stateMachine { } blocks found in configured sources.")
            return
        }

        for (model in models) {
            val puml = emitter.emit(model)
            val fileName = "${model.name}.puml"
            out.resolve(fileName).writeText(puml)
            logger.lifecycle("Monaka: wrote $fileName")
        }
    }
}
