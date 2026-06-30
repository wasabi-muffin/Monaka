package dev.gmvalentino.monaka.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.*
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.net.URLClassLoader

@DisableCachingByDefault(because = "outputs may be written to source directories")
abstract class GenerateYamlTask : DefaultTask() {

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
        val filePaths = sources.asFileTree.files.map { it.absolutePath }

        // Gradle's InstrumentingVisitableURLClassLoader fails to load the shaded IntelliJ
        // platform classes (e.g. Disposable) from kotlin-compiler-embeddable at runtime.
        // Creating a fresh URLClassLoader from the same URL list bypasses the instrumentation
        // and allows KotlinCoreEnvironment to initialize without error.
        val pluginCL = javaClass.classLoader as URLClassLoader
        val freshCL = URLClassLoader(pluginCL.urLs, ClassLoader.getPlatformClassLoader())

        val bridgeClass = freshCL.loadClass("dev.gmvalentino.monaka.gradle.parser.PsiParserBridge")
        val parseMethod = bridgeClass.getDeclaredMethod("parseToYaml", List::class.java)

        @Suppress("UNCHECKED_CAST")
        val results = parseMethod.invoke(null, filePaths) as List<Map<String, String>>

        if (results.isEmpty()) {
            logger.lifecycle("Monaka: no stateMachine { } blocks found in configured sources.")
            return
        }

        for (result in results) {
            val sourceFile = File(result["sourceFile"]!!)
            val name = result["name"]!!
            val yaml = result["yaml"]!!
            val out = fixedOutputDir ?: sourceFile.parentFile
            val primary = out.resolve("$name.yaml")
            val target = if (primary.exists()) out.resolve("$name.gen.yaml") else primary
            target.writeText(yaml)
            logger.lifecycle("Monaka: wrote ${target.name}")
        }
    }
}
