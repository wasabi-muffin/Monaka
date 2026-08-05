import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Declares the KMP targets for Compose-based library modules (e.g. :monaka-compose).
 *
 * This is the [KmpTargetsConventionPlugin] set intersected with the targets that
 * Compose Multiplatform actually publishes for. Compared to the core library set,
 * three groups are dropped because `org.jetbrains.compose.runtime:runtime` (and, for
 * `watchosDeviceArm64`, `org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose`)
 * do not publish artifacts for them:
 *   - iosX64              — Compose runtime no longer publishes the Intel iOS simulator.
 *   - watchosDeviceArm64  — not published by Compose runtime.
 *   - androidNative*      — not published by Compose runtime or lifecycle-runtime-compose.
 *
 * Targets:
 *   iOS     — iosArm64, iosSimulatorArm64
 *   macOS   — macosArm64
 *   watchOS — watchosArm32, watchosArm64, watchosSimulatorArm64
 *   tvOS    — tvosArm64, tvosSimulatorArm64
 *   JVM     — jvm
 *   Linux   — linuxArm64, linuxX64
 *   Windows — mingwX64
 *   JS/Wasm — js (browser + node), wasmJs (browser + node)
 *
 * Also applies `monaka.kmp.library`, which sets up applyDefaultHierarchyTemplate().
 */
@OptIn(ExperimentalWasmDsl::class)
class KmpComposeTargetsConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("monaka.kmp.library")

            extensions.configure<KotlinMultiplatformExtension> {
                // iOS
                iosArm64()
                iosSimulatorArm64()

                // macOS
                macosArm64()

                // watchOS
                watchosArm32()
                watchosArm64()
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
