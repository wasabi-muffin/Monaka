package tech.fika.monaka.gradle

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property

abstract class MonakaStubsExtension {
    /** Path to a YAML file or directory of YAML files to generate stubs from. */
    abstract val input: Property<String>

    /**
     * Directory to write generated `.kt` files.
     * Defaults to the same directory as each source YAML file.
     */
    abstract val outputDir: DirectoryProperty

    /** Whether to emit a class or a top-level val. Defaults to [StubStyle.CLASS]. */
    abstract val style: Property<StubStyle>

    /**
     * When `false` (default), skip files that already exist on disk.
     * When `true`, overwrite any existing files.
     */
    abstract val replace: Property<Boolean>

    /**
     * When `true` (default), emit `@SelfTransition` on the root sealed interface and
     * `@Transition(...)` on each substate, based on the transitions declared in the YAML model.
     */
    abstract val useTransitionAnnotation: Property<Boolean>
}
