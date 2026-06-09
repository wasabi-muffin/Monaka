plugins {
    alias(libs.plugins.monaka.kmp.library)
    alias(libs.plugins.monaka.compose)
    alias(libs.plugins.compiler.plugin)
    alias(libs.plugins.ksp)
    id("dev.gmvalentino.monaka.yaml-export")
}

kotlin {
    android {
        namespace = "dev.gmvalentino.monaka.sample"
        compileSdk = 37
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
            binaryOption("bundleId", "dev.gmvalentino.monaka.shared")
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
            implementation(libs.compose.material.icons.core)
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

dependencies {
    add("kspCommonMainMetadata", project(":monaka-processor"))
}

kotlin.sourceSets.commonMain {
    kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    if (name != "kspCommonMainKotlinMetadata") {
        dependsOn("kspCommonMainKotlinMetadata")
    }
}

monakaYamlExport {
    sources.from(
        kotlin.sourceSets["commonMain"].kotlin.srcDirs
    )
}
