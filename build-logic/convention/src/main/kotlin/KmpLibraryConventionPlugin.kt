import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Base convention for every KMP library module.
 *
 * Applies:
 *   - org.jetbrains.kotlin.multiplatform
 *   - com.android.kotlin.multiplatform.library
 *
 * Configures:
 *   - applyDefaultHierarchyTemplate()
 *
 * Each module must still declare:
 *   android { namespace = "…"; compileSdk = 36; minSdk = 24; compilerOptions { jvmTarget = JVM_11 } }
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            pluginManager.apply("com.android.kotlin.multiplatform.library")

            extensions.configure<KotlinMultiplatformExtension> {
                applyDefaultHierarchyTemplate()
            }
        }
    }
}
