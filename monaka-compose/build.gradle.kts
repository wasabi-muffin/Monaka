plugins {
    alias(libs.plugins.monaka.kmp.library)
    alias(libs.plugins.monaka.compose)
    alias(libs.plugins.monaka.explicit.api)
    alias(libs.plugins.monaka.publication)
}

mavenPublishing {
    pom {
        description.set(
            "Compose Multiplatform helpers for Monaka: rememberStore, toViewStore, handleEffects, render, and lifecycle binding.",
        )
    }
}

kotlin {
    android {
        namespace = "dev.gmvalentino.monaka.compose"
        compileSdk = Config.COMPILE_SDK
        minSdk = Config.MIN_SDK
    }

    jvm()

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":monaka"))
            implementation(libs.compose.runtime)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        // Compose UI tests run on the JVM (desktop) target only — they need the
        // host Skia renderer (compose.desktop.currentOs), which the plain jvm()
        // KMP target does not pull in on its own.
        jvmTest.dependencies {
            implementation(libs.compose.ui.test)
            implementation(compose.desktop.currentOs)
        }
    }
}
