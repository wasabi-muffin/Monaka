import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Declares the full set of KMP targets for the core :monaka library.
 *
 * Targets:
 *   iOS     — iosArm64, iosSimulatorArm64, iosX64
 *   macOS   — macosArm64
 *   watchOS — watchosArm32, watchosArm64, watchosDeviceArm64, watchosSimulatorArm64
 *   tvOS    — tvosArm64, tvosSimulatorArm64
 *   JVM     — jvm
 *   Linux   — linuxArm64, linuxX64
 *   Windows — mingwX64
 *   Android Native — androidNativeArm32/64, androidNativeX64/86
 *   JS/Wasm — js (browser + node), wasmJs (browser + node), wasmWasi (node)
 *
 * Also applies `monaka.kmp.library`, which sets up applyDefaultHierarchyTemplate().
 */
@OptIn(ExperimentalWasmDsl::class)
class KmpTargetsConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("monaka.kmp.library")

            extensions.configure<KotlinMultiplatformExtension> {
                // iOS
                iosArm64()
                iosSimulatorArm64()
                iosX64()

                // macOS
                macosArm64()

                // watchOS
                watchosArm32()
                watchosArm64()
                watchosDeviceArm64()
                watchosSimulatorArm64()

                // tvOS
                tvosArm64()
                tvosSimulatorArm64()

                // JVM / Desktop
                jvm()

                // Linux
                linuxArm64()
                linuxX64()

                // Windows
                mingwX64()

                // Android Native
                androidNativeArm32()
                androidNativeArm64()
                androidNativeX64()
                androidNativeX86()

                // JavaScript / WebAssembly
                js {
                    browser()
                    nodejs()
                }
                wasmJs {
                    browser()
                    nodejs()
                }
            }
        }
    }
}
