package dev.gmvalentino.monaka.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

class MonakaPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        val yamlExtension = target.extensions.create(
            "monakaYamlGenerator",
            MonakaYamlExtension::class.java,
        )

        target.tasks.register(
            "generateMonakaYaml",
            GenerateYamlTask::class.java,
        ) { task ->
            task.group = "monaka"
            task.description = "Generates YAML documentation from stateMachine { } DSL blocks."
            task.sources.setFrom(yamlExtension.sources)
            task.outputDir.set(yamlExtension.yamlOutputDir)
        }

        val pumlExtension = target.extensions.create(
            "monakaPumlGenerator",
            MonakaPumlExtension::class.java,
        )

        target.tasks.register(
            "generateMonakaPuml",
            GeneratePumlTask::class.java,
        ) { task ->
            task.group = "monaka"
            task.description = "Generates PlantUML state diagrams from YAML state machine definitions."
            task.sources.from(
                target.provider {
                    if (yamlExtension.yamlOutputDir.isPresent)
                        target.fileTree(yamlExtension.yamlOutputDir) { spec -> spec.include("**/*.yaml") }
                    else
                        yamlExtension.sources.asFileTree.matching { spec -> spec.include("**/*.yaml") }
                }
            )
            task.outputDir.set(pumlExtension.pumlOutputDir)
        }

        val stubsExtension = target.extensions.create(
            "monakaStubGenerator",
            MonakaStubsExtension::class.java,
        )

        stubsExtension.input.convention(target.projectDir.absolutePath)
        stubsExtension.style.convention(StubStyle.CLASS)
        stubsExtension.replace.convention(false)
        stubsExtension.useTransitionAnnotation.convention(true)

        target.tasks.register(
            "generateMonakaStubs",
            GenerateStubsTask::class.java,
        ) { task ->
            task.group = "monaka"
            task.description = "Generates Kotlin stub files (State, Action, Effect, StateMachine) from YAML definitions."
            task.extensionInput.set(stubsExtension.input)
            task.extensionOutputDir.set(stubsExtension.outputDir)
            task.extensionStyle.set(stubsExtension.style)
            task.extensionReplace.set(stubsExtension.replace)
            task.extensionUseTransitionAnnotation.set(stubsExtension.useTransitionAnnotation)
        }
    }
}
