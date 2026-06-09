plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.monaka.publication)
}

mavenPublishing {
    pom {
        description.set("KSP annotation processor for Monaka that generates type-safe transition functions from @Transition and @SelfTransition annotations.")
    }
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    implementation(libs.ksp.api)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)
}
