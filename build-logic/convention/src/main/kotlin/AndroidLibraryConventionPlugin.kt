import Config.jvmTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget

/**
 * Convention for Android library targets in KMP modules.
 *
 * Applies:
 *   - com.android.kotlin.multiplatform.library
 *
 * Configures:
 *   - androidTarget compilerOptions: jvmTarget JVM_11
 *
 * Each module must still declare:
 *   android { namespace = "…"; compileSdk = Config.COMPILE_SDK; minSdk = Config.MIN_SDK }
 *
 * Note: compileSdk and minSdk cannot be set here — AGP only exposes them via the
 * android { } extension function inside kotlin { }, which is not accessible from
 * convention plugin code.
 *
 * Note: androidTarget { } is intentionally avoided here — AGP registers the android
 * target using the 'android' preset, and calling androidTarget { } would attempt to
 * re-create it with a different preset, causing a conflict. Instead we configure the
 * existing target via targets.withType<KotlinAndroidTarget>().
 */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.kotlin.multiplatform.library")

            extensions.configure<KotlinMultiplatformExtension> {
                targets.withType<KotlinAndroidTarget>().configureEach {
                    compilerOptions {
                        jvmTarget.set(Config.jvmTarget)
                    }
                }
            }
        }
    }
}
