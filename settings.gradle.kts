pluginManagement {
    includeBuild("build-logic")
    includeBuild("monaka-gradle-plugin")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        mavenLocal()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        mavenLocal()
        // Kotlin/JS toolchain: Node.js runtime
        ivy("https://nodejs.org/dist/") {
            name = "Node Distributions at nodejs.org"
            patternLayout { artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]") }
            metadataSources { artifact() }
            content { includeModule("org.nodejs", "node") }
        }
        // Kotlin/JS toolchain: Yarn package manager
        ivy("https://github.com/yarnpkg/yarn/releases/download") {
            name = "Yarn Distributions at github.com"
            patternLayout { artifact("v[revision]/[artifact](-v[revision]).[ext]") }
            metadataSources { artifact() }
            content { includeModule("com.yarnpkg", "yarn") }
        }
    }
}

rootProject.name = "Monaka"
includeBuild("monaka-gradle-plugin")
include(":monaka")
include(":monaka-test")
include(":sample:shared")
include(":sample:androidApp")
