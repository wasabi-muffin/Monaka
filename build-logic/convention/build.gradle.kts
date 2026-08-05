plugins {
    `kotlin-dsl`
}

group = "dev.gmvalentino.monaka.buildlogic"

dependencies {
    implementation(libs.gradle.android)
    implementation(libs.gradle.kotlin)
    implementation(libs.gradle.compose.multiplatform)
    implementation(libs.gradle.vanniktech.publish)
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
        register("monakaKmpComposeTargets") {
            id = "monaka.kmp.compose.targets"
            implementationClass = "KmpComposeTargetsConventionPlugin"
        }
        register("monakaCompose") {
            id = "monaka.compose"
            implementationClass = "ComposeConventionPlugin"
        }
        register("monakaAndroidLibrary") {
            id = "monaka.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("monakaExplicitApi") {
            id = "monaka.explicit.api"
            implementationClass = "ExplicitApiConventionPlugin"
        }
        register("monakaAndroidApplication") {
            id = "monaka.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
    }
}
