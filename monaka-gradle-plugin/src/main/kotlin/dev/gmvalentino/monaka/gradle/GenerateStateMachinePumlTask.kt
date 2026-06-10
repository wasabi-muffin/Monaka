package dev.gmvalentino.monaka.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.*
import org.gradle.work.DisableCachingByDefault
import dev.gmvalentino.monaka.gradle.emit.PumlEmitter
import dev.gmvalentino.monaka.gradle.parser.KtSourceParser
import dev.gmvalentino.monaka.gradle.parser.YamlParser

@DisableCachingByDefault(because = "outputs may be written to source directories")
abstract class GenerateStateMachinePumlTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:SkipWhenEmpty
    abstract val sources: ConfigurableFileCollection

    /**
     * Directory where generated `.puml` files are written.
     *
     * When not set (the default), each `.puml` file is written to the same directory
     * as the source file that contains the `stateMachine { }` block.
     * When set, all `.puml` files are written to this single directory.
     */
    @get:Optional
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val fixedOutputDir = if (outputDir.isPresent) outputDir.get().asFile.also { it.mkdirs() } else null
        val emitter = PumlEmitter()
        val pairs = KtSourceParser().parseFiles(sources.asFileTree.files)

        if (pairs.isEmpty()) {
            logger.lifecycle("Monaka: no stateMachine { } blocks found in configured sources.")
            return
        }

        val yamlParser = YamlParser()
        for ((sourceFile, model) in pairs) {
            val out = fixedOutputDir ?: sourceFile.parentFile
            val genYaml = out.resolve("${model.name}.gen.yaml")
            val resolvedModel = if (genYaml.exists()) {
                logger.lifecycle("Monaka: using ${genYaml.name} as source for puml")
                yamlParser.parse(genYaml.readText())
            } else {
                model
            }
            val puml = emitter.emit(resolvedModel)
            val primary = out.resolve("${model.name}.puml")
            val target = if (primary.exists()) out.resolve("${model.name}.gen.puml") else primary
            target.writeText(puml)
            logger.lifecycle("Monaka: wrote ${target.name}")
        }
    }
}
