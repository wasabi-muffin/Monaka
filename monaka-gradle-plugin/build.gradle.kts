plugins {
    `java-gradle-plugin`
    kotlin("jvm") version "2.3.21"
}

group = "tech.fika.monaka"
version = "0.1.0"

dependencies {
    compileOnly(gradleApi())
    testImplementation(kotlin("test"))
    testImplementation(gradleApi())
}

gradlePlugin {
    plugins {
        register("monakaYamlExport") {
            id = "tech.fika.monaka.yaml-export"
            implementationClass = "tech.fika.monaka.gradle.MonakaPlugin"
        }
    }
}
