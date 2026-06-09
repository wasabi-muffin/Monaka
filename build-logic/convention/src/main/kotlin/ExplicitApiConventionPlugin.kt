import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Enables Kotlin explicit API mode for KMP library modules.
 *
 * Configures:
 *   - explicitApi() — requires all public declarations to have explicit visibility
 *     modifiers and return types.
 *
 * Applied automatically by [KmpLibraryConventionPlugin]. Can also be applied
 * independently to any module that uses the kotlin.multiplatform plugin.
 */
class ExplicitApiConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            extensions.configure<KotlinMultiplatformExtension> {
                explicitApi()
            }
        }
    }
}
