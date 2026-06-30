package dev.gmvalentino.monaka.gradle.parser

import dev.gmvalentino.monaka.gradle.writer.YamlWriter
import java.io.File

/**
 * Entry point for PSI-based YAML generation when called across a classloader boundary.
 *
 * [dev.gmvalentino.monaka.gradle.GenerateYamlTask] loads this class from a fresh [java.net.URLClassLoader]
 * (constructed from the plugin classloader's URL list) to bypass Gradle's
 * [org.gradle.internal.classloader.VisitableURLClassLoader] instrumentation, which
 * cannot handle the shaded IntelliJ platform classes inside `kotlin-compiler-embeddable`.
 *
 * All parameters and return values use JDK-only types so they are compatible across the
 * classloader boundary without any serialization.
 */
internal object PsiParserBridge {
    /**
     * Parse [filePaths] with the PSI-based parser and emit YAML for each state machine
     * found. Returns one entry per state machine with keys `"sourceFile"`, `"name"`,
     * and `"yaml"`.
     */
    @JvmStatic
    fun parseToYaml(filePaths: List<String>): List<Map<String, String>> {
        val parser = PsiStateMachineParser()
        val emitter = YamlWriter()
        return parser.parseFiles(filePaths.map(::File)).map { (file, model) ->
            mapOf(
                "sourceFile" to file.absolutePath,
                "name" to model.name,
                "yaml" to emitter.write(model),
            )
        }
    }
}
