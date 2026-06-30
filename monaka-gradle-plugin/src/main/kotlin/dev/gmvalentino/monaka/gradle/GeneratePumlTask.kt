package dev.gmvalentino.monaka.gradle

import dev.gmvalentino.monaka.gradle.parser.YamlParser
import dev.gmvalentino.monaka.gradle.writer.PumlWriter
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "outputs may be written to source directories")
abstract class GeneratePumlTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:SkipWhenEmpty
    abstract val sources: ConfigurableFileCollection

    /**
     * Directory where generated `.puml` files are written.
     *
     * When not set (the default), each `.puml` file is written to the same directory
     * as the `.yaml` file it was generated from.
     * When set, all `.puml` files are written to this single directory.
     */
    @get:Optional
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val fixedOutputDir = if (outputDir.isPresent) outputDir.get().asFile.also { it.mkdirs() } else null
        val yamlParser = YamlParser()
        val writer = PumlWriter()

        val allYamlFiles = sources.asFileTree.matching { it.include("**/*.yaml") }.files
        if (allYamlFiles.isEmpty()) {
            logger.lifecycle("Monaka: no .yaml files found in configured sources.")
            return
        }

        // For each base name, prefer hand-edited (.yaml) over auto-generated (.gen.yaml).
        val byBaseName = LinkedHashMap<String, File>()
        for (file in allYamlFiles) {
            val isGen = file.name.endsWith(".gen.yaml")
            val baseName = if (isGen) file.name.removeSuffix(".gen.yaml") else file.name.removeSuffix(".yaml")
            if (!isGen || !byBaseName.containsKey(baseName)) {
                byBaseName[baseName] = file
            }
        }

        for ((_, yamlFile) in byBaseName) {
            val model = yamlParser.parse(yamlFile.readText())
            if (model.name.isBlank()) {
                logger.lifecycle("Monaka: skipping ${yamlFile.name} — no machine name found")
                continue
            }
            val out = fixedOutputDir ?: yamlFile.parentFile
            val puml = writer.write(model)
            val primary = out.resolve("${model.name}.puml")
            val target = if (primary.exists()) out.resolve("${model.name}.gen.puml") else primary
            target.writeText(puml)
            logger.lifecycle("Monaka: wrote ${target.name} (from ${yamlFile.name})")
        }
    }
}
