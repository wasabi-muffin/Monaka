package tech.fika.monaka.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.*
import tech.fika.monaka.gradle.emit.YamlEmitter
import tech.fika.monaka.gradle.parser.KtSourceParser

@CacheableTask
abstract class GenerateStateMachineYamlTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:SkipWhenEmpty
    abstract val sources: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val out = outputDir.get().asFile.also { it.mkdirs() }
        val emitter = YamlEmitter()
        val models = KtSourceParser().parseFiles(sources.asFileTree.files)

        if (models.isEmpty()) {
            logger.lifecycle("Monaka: no stateMachine { } blocks found in configured sources.")
            return
        }

        for (model in models) {
            val yaml = emitter.emit(model)
            val fileName = "${model.name}.yaml"
            out.resolve(fileName).writeText(yaml)
            logger.lifecycle("Monaka: wrote $fileName")
        }
    }
}
