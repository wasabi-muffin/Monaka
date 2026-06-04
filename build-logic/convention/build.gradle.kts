plugins {
    `kotlin-dsl`
}

group = "tech.fika.monaka.buildlogic"

dependencies {
    implementation(libs.gradle.android)
    implementation(libs.gradle.kotlin)
    implementation(libs.gradle.compose.multiplatform)
}

gradlePlugin {
    plugins {
        register("monakaKmpLibrary") {
            id = "monaka.kmp.library"
            implementationClass = "KmpLibraryConventionPlugin"
        }
        register("monakaKmpPublishable") {
            id = "monaka.publication"
            implementationClass = "PublicationConventionPlugin"
        }
        register("monakaKmpTargets") {
            id = "monaka.kmp.targets"
            implementationClass = "KmpTargetsConventionPlugin"
        }
        register("monakaCompose") {
            id = "monaka.compose"
            implementationClass = "ComposeConventionPlugin"
        }
        register("monakaAndroidApplication") {
            id = "monaka.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
    }
}
