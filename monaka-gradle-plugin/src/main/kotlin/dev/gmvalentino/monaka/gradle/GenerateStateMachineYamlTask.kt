package dev.gmvalentino.monaka.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.*
import org.gradle.work.DisableCachingByDefault
import dev.gmvalentino.monaka.gradle.emit.YamlEmitter
import dev.gmvalentino.monaka.gradle.parser.KtSourceParser

@DisableCachingByDefault(because = "outputs may be written to source directories")
abstract class GenerateStateMachineYamlTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:SkipWhenEmpty
    abstract val sources: ConfigurableFileCollection

    /**
     * Directory where generated `.yaml` files are written.
     *
     * When not set (the default), each `.yaml` file is written to the same directory
     * as the source file that contains the `stateMachine { }` block.
     * When set, all `.yaml` files are written to this single directory.
     */
    @get:Optional
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val fixedOutputDir = if (outputDir.isPresent) outputDir.get().asFile.also { it.mkdirs() } else null
        val emitter = YamlEmitter()
        val pairs = KtSourceParser().parseFiles(sources.asFileTree.files)

        if (pairs.isEmpty()) {
            logger.lifecycle("Monaka: no stateMachine { } blocks found in configured sources.")
            return
        }

        for ((sourceFile, model) in pairs) {
            val out = fixedOutputDir ?: sourceFile.parentFile
            val yaml = emitter.emit(model)
            val primary = out.resolve("${model.name}.yaml")
            val target = if (primary.exists()) out.resolve("${model.name}.gen.yaml") else primary
            target.writeText(yaml)
            logger.lifecycle("Monaka: wrote ${target.name}")
        }
    }
}
