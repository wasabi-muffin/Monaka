package dev.gmvalentino.monaka.gradle

import org.gradle.api.file.DirectoryProperty

abstract class MonakaPumlExtension {
    /**
     * Directory where generated `.puml` files are written.
     * Defaults to the same directory as each source file when not set.
     */
    abstract val pumlOutputDir: DirectoryProperty
}
