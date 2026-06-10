plugins {
    alias(libs.plugins.monaka.kmp.library)
    alias(libs.plugins.monaka.compose)
    alias(libs.plugins.compiler.plugin)
    alias(libs.plugins.ksp)
    alias(libs.plugins.monaka.gradle.plugin)
}

kotlin {
    android {
        namespace = "dev.gmvalentino.monaka.sample"
        compileSdk = Config.COMPILE_SDK
        minSdk = Config.MIN_SDK
    }

    jvm()

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
            implementation(libs.navigation3.ui)

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
    add("kspCommonMainMetadata", project(":monaka-transitions"))
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
