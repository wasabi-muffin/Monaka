import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest

plugins {
    alias(libs.plugins.monaka.kmp.targets)
    alias(libs.plugins.monaka.explicit.api)
    alias(libs.plugins.monaka.publication)
}

// Skip simulator tests for targets whose SDK is not installed on the current machine.
// Tests still compile for all targets; only execution is gated.
tasks.withType<KotlinNativeSimulatorTest>().configureEach {
    enabled = runCatching { device.get() }.isSuccess
}

kotlin {
    android {
        namespace = "dev.gmvalentino.monaka.library"
        compileSdk = Config.COMPILE_SDK
        minSdk = Config.MIN_SDK
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.log.kermit)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
    }
}
