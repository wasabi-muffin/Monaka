import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * Convention for Android application modules.
 *
 * Applies:
 *   - com.android.application
 *   - org.jetbrains.kotlin.plugin.compose
 *
 * Configures shared defaults:
 *   - compileSdk 37 / minSdk 24 / targetSdk 36
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
                compileSdk = Config.COMPILE_SDK

                defaultConfig {
                    minSdk = Config.MIN_SDK
                    targetSdk = Config.TARGET_SDK
                }

                compileOptions {
                    sourceCompatibility = Config.javaVersion
                    targetCompatibility = Config.javaVersion
                }

                buildFeatures {
                    compose = true
                }
            }

            tasks.withType(KotlinCompile::class.java).configureEach {
                compilerOptions.freeCompilerArgs.add("-Xskip-prerelease-check")
            }
        }
    }
}
