import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Convention for Android application modules.
 *
 * Applies:
 *   - com.android.application
 *   - org.jetbrains.kotlin.plugin.compose
 *
 * Configures shared defaults:
 *   - compileSdk 36 / minSdk 24 / targetSdk 36
 *   - Java 11 source/target compatibility
 *   - Compose build feature enabled
 *
 * Each module must still declare:
 *   android { namespace, defaultConfig.applicationId, defaultConfig.versionCode/Name, … }
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            extensions.configure<ApplicationExtension> {
                compileSdk = 36

                defaultConfig {
                    minSdk = 24
                    targetSdk = 36
                }

                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_11
                    targetCompatibility = JavaVersion.VERSION_11
                }

                buildFeatures {
                    compose = true
                }
            }
        }
    }
}
