import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Base convention for every KMP library module.
 *
 * Applies:
 *   - org.jetbrains.kotlin.multiplatform
 *   - monaka.android.library (via [AndroidLibraryConventionPlugin])
 *   - monaka.explicit.api (via [ExplicitApiConventionPlugin])
 *
 * Configures:
 *   - applyDefaultHierarchyTemplate()
 *
 * Each module must still declare:
 *   android { namespace = "…"; compileSdk = Config.COMPILE_SDK; minSdk = Config.MIN_SDK }
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            pluginManager.apply("monaka.android.library")

            extensions.configure<KotlinMultiplatformExtension> {
                compilerOptions {
                    apiVersion.set(Config.kotlinApiVersion)
                    languageVersion.set(Config.kotlinLanguageVersion)
                }
                applyDefaultHierarchyTemplate()
            }
        }
    }
}
