plugins {
    `java-gradle-plugin`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.monaka.publication)
}

dependencies {
    compileOnly(gradleApi())
    testImplementation(gradleApi())
    testImplementation(kotlin("test"))
}

gradlePlugin {
    plugins {
        register("monakaGradlePlugin") {
            id = "dev.gmvalentino.monaka.gradle-plugin"
            implementationClass = "dev.gmvalentino.monaka.gradle.MonakaPlugin"
        }
    }
}
