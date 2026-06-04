plugins {
    alias(libs.plugins.monaka.kmp.library)
    alias(libs.plugins.monaka.compose)
    alias(libs.plugins.compiler.plugin)
}

kotlin {
    android {
        namespace = "tech.fika.monaka.sample"
        compileSdk = 36
        minSdk = 24
        compilerOptions {
            jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
        }
    }

    jvm()

    // Compose Multiplatform 1.11+ no longer publishes iosX64 binaries (Intel Mac
    // simulators). Apple-silicon Macs (M1+) use iosSimulatorArm64 for the simulator
    // and iosArm64 for physical devices.
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "shared"
            isStatic = true
            binaryOption("bundleId", "tech.fika.monaka.shared")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":monaka"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.lifecycle.viewmodel)

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.components.ui.tooling.preview)
        }
        commonTest.dependencies {
            implementation(project(":monaka-test"))
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
        }
    }
}
