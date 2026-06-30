import org.jetbrains.kotlin.gradle.internal.builtins.StandardNames.FqNames.target

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.binary.compatibility.validator)
    alias(libs.plugins.spotless)
}

spotless {
    val ktlintVersion = libs.versions.ktlint.get()

    val ktlintConfig = mapOf(
        "ktlint_code_style" to "intellij_idea",
        "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
        "ktlint_standard_filename" to "disabled",
    )
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**")
        ktlint(ktlintVersion).editorConfigOverride(ktlintConfig)
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint(ktlintVersion).editorConfigOverride(ktlintConfig)
    }
}

apiValidation {
    // Only validate the published library modules; exclude samples and internal tooling
    ignoredProjects += listOf(
        "androidApp",
        "shared",
        "monaka-transitions",
    )
}

// ── MkDocs documentation tasks ───────────────────────────────────────────────

val venvDir = layout.projectDirectory.dir(".venv")
val mkdocsBin = layout.projectDirectory.file(".venv/bin/mkdocs")

tasks.register<Exec>("mkdocsInstall") {
    group = "documentation"
    description = "Creates .venv and installs mkdocs-material from requirements.txt."
    inputs.file(layout.projectDirectory.file("requirements.txt"))
    outputs.file(mkdocsBin)
    commandLine(
        "sh",
        "-c",
        "python3 -m venv ${venvDir.asFile.absolutePath} && " +
            "${venvDir.file("bin/pip").asFile.absolutePath} install --quiet " +
            "-r ${layout.projectDirectory.file("requirements.txt").asFile.absolutePath}",
    )
}

tasks.register<Exec>("mkdocsBuild") {
    group = "documentation"
    description = "Builds the MkDocs static site into site/."
    dependsOn("mkdocsInstall")
    inputs.file(layout.projectDirectory.file("mkdocs.yml"))
    inputs.dir(layout.projectDirectory.dir("docs"))
    outputs.dir(layout.projectDirectory.dir("site"))
    commandLine(mkdocsBin.asFile.absolutePath, "build", "--strict")
}

tasks.register<Exec>("mkdocsServe") {
    group = "documentation"
    description = "Starts the MkDocs dev server with live reload at http://127.0.0.1:8000."
    dependsOn("mkdocsInstall")
    commandLine(mkdocsBin.asFile.absolutePath, "serve")
}
