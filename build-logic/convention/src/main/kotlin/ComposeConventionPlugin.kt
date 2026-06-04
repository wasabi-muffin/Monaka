import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Adds Compose Multiplatform to a KMP module.
 *
 * Applies:
 *   - org.jetbrains.kotlin.plugin.compose  (Kotlin Compose compiler plugin)
 *   - org.jetbrains.compose               (JetBrains Compose Multiplatform)
 *
 * Expects `monaka.kmp.library` to be applied first so the kotlin {} extension
 * is already configured.
 */
class ComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            pluginManager.apply("org.jetbrains.compose")
        }
    }
}
