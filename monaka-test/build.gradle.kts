plugins {
    alias(libs.plugins.monaka.kmp.targets)
    alias(libs.plugins.monaka.kmp.library)
    alias(libs.plugins.monaka.explicit.api)
    alias(libs.plugins.monaka.publication)
}

mavenPublishing {
    pom {
        description.set("Test DSL for Monaka state machines that includes assertion helpers and testStore builder for verifying states, effects, and actions.")
    }
}

kotlin {
    android {
        namespace = "dev.gmvalentino.monaka.test.library"
        compileSdk = Config.COMPILE_SDK
        minSdk = Config.MIN_SDK
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":monaka"))
            api(libs.kotlinx.coroutines.test)
            api(libs.turbine)
            implementation(libs.kotest.assertions)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
