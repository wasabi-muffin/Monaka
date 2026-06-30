package dev.gmvalentino.monaka.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault
import dev.gmvalentino.monaka.gradle.writer.KotlinStubWriter
import dev.gmvalentino.monaka.gradle.parser.YamlParser
import java.io.File
import javax.inject.Inject

@DisableCachingByDefault(because = "outputs are written to source directories")
abstract class GenerateStubsTask @Inject constructor(
    objects: ObjectFactory,
) : DefaultTask() {

    // ── CLI options (concrete — required for @Option + Gradle decoration) ──────

    @get:Input
    @get:Optional
    val cliInput: Property<String> = objects.property(String::class.java)

    @Option(option = "input", description = "Path to a YAML file or directory of YAML files")
    fun setCliInput(value: String) = cliInput.set(value)

    @get:Input
    @get:Optional
    val cliOutput: Property<String> = objects.property(String::class.java)

    @Option(option = "output", description = "Directory to write generated Kotlin stubs (default: same dir as YAML)")
    fun setCliOutput(value: String) = cliOutput.set(value)

    @get:Input
    @get:Optional
    val cliStyle: Property<String> = objects.property(String::class.java)

    @Option(option = "style", description = "Generation style: 'class' (default) or 'factory'")
    fun setCliStyle(value: String) = cliStyle.set(value)

    @get:Input
    @get:Optional
    val cliReplace: Property<Boolean> = objects.property(Boolean::class.java)

    @Option(option = "replace", description = "Overwrite existing stub files (default: false)")
    fun setCliReplace(value: Boolean) = cliReplace.set(value)

    @get:Input
    @get:Optional
    val cliUseTransitionAnnotation: Property<Boolean> = objects.property(Boolean::class.java)

    @Option(option = "use-transition-annotation", description = "Emit @SelfTransition on the root sealed interface and @Transition(...) on each substate (default: true)")
    fun setCliUseTransitionAnnotation(value: Boolean) = cliUseTransitionAnnotation.set(value)

    // ── Extension defaults (wired by MonakaPlugin) ────────────────────────────

    @get:Internal
    abstract val extensionInput: Property<String>

    @get:Internal
    abstract val extensionOutputDir: DirectoryProperty

    @get:Internal
    abstract val extensionStyle: Property<StubStyle>

    @get:Internal
    abstract val extensionReplace: Property<Boolean>

    @get:Internal
    abstract val extensionUseTransitionAnnotation: Property<Boolean>

    // ── Action ────────────────────────────────────────────────────────────────

    @TaskAction
    fun generate() {
        val inputPath = cliInput.orNull ?: extensionInput.orNull
            ?: error("Monaka: no input specified. Use --input=<path> or configure monakaStubGenerator { input.set(...) }")

        val resolvedInput = project.file(inputPath)
        val yamlFiles: List<File> = when {
            resolvedInput.isFile && resolvedInput.extension == "yaml" -> listOf(resolvedInput)
            resolvedInput.isDirectory ->
                resolvedInput.walkTopDown()
                    .filter { it.isFile && it.extension == "yaml" && !it.name.endsWith(".gen.yaml") }
                    .toList()
            else -> {
                logger.warn("Monaka: input does not exist or is not a YAML file/directory: $inputPath")
                return
            }
        }

        if (yamlFiles.isEmpty()) {
            logger.lifecycle("Monaka: no YAML files found at $inputPath")
            return
        }

        val fixedOutputDir: File? = cliOutput.orNull?.let { project.file(it) }
            ?: extensionOutputDir.orNull?.asFile

        val style = resolveStyle()
        val replace = cliReplace.orNull ?: extensionReplace.orNull ?: false
        val useTransitionAnnotation = cliUseTransitionAnnotation.orNull
            ?: extensionUseTransitionAnnotation.orNull ?: true
        val parser = YamlParser()
        val emitter = KotlinStubWriter()

        for (yamlFile in yamlFiles) {
            val model = parser.parse(yamlFile.readText())
            if (model.name.isBlank()) {
                logger.lifecycle("Monaka: skipped ${yamlFile.name} (no name defined)")
                continue
            }
            val outDir = fixedOutputDir ?: yamlFile.parentFile
            outDir.mkdirs()

            val pkg = inferPackage(yamlFile)
            val files = emitter.write(model, style, pkg, useTransitionAnnotation)

            for (file in files) {
                val out = outDir.resolve(file.name)
                if (out.exists() && !replace) {
                    logger.lifecycle("Monaka: skipped ${out.absolutePath} (already exists, use --replace to overwrite)")
                    continue
                }
                out.writeText(file.content)
                logger.lifecycle("Monaka: wrote ${out.absolutePath}")
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun resolveStyle(): StubStyle {
        val raw = cliStyle.orNull ?: return extensionStyle.orNull ?: StubStyle.CLASS
        return StubStyle.values().firstOrNull { it.name.equals(raw, ignoreCase = true) }
            ?: error("Monaka: unknown style '$raw'. Use 'class' or 'factory'.")
    }

    /**
     * Infers the Kotlin package from the yaml file's location by scanning parent
     * directories for a known source-set root (e.g. `src/commonMain/kotlin/`).
     * Returns null if no source root can be detected.
     */
    private fun inferPackage(yamlFile: File): String? {
        val parts = yamlFile.absolutePath.replace('\\', '/').split('/')
        val sourceRoots = setOf("kotlin", "java", "resources")
        val sourceSets = setOf(
            "main", "test",
            "commonMain", "commonTest",
            "androidMain", "androidTest",
            "iosMain", "iosTest",
            "jvmMain", "jvmTest",
        )
        for (i in 1 until parts.size) {
            if (parts[i] in sourceRoots && parts[i - 1] in sourceSets) {
                val pkgParts = parts.drop(i + 1).dropLast(1) // strip filename
                return pkgParts.joinToString(".").takeIf { it.isNotEmpty() }
            }
        }
        return null
    }
}
