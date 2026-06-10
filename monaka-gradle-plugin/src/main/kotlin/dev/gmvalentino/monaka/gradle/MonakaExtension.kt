package dev.gmvalentino.monaka.gradle

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty

abstract class MonakaExtension {
    /** Kotlin source files to scan for `stateMachine { }` blocks. */
    abstract val sources: ConfigurableFileCollection

    /**
     * Directory where generated `.yaml` files are written.
     * Defaults to the same directory as each source file when not set.
     */
    abstract val outputDir: DirectoryProperty

    /**
     * Directory where generated `.puml` files are written.
     * Defaults to the same directory as each source file when not set.
     */
    abstract val pumlOutputDir: DirectoryProperty
}
