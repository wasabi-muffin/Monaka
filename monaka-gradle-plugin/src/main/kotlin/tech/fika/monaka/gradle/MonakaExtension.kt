package tech.fika.monaka.gradle

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty

abstract class MonakaExtension {
    /** Kotlin source files to scan for `stateMachine { }` blocks. */
    abstract val sources: ConfigurableFileCollection

    /** Directory where generated `.yaml` files are written. Defaults to `build/monaka-yaml`. */
    abstract val outputDir: DirectoryProperty

    /** Directory where generated `.puml` files are written. Defaults to `build/monaka-puml`. */
    abstract val pumlOutputDir: DirectoryProperty
}
