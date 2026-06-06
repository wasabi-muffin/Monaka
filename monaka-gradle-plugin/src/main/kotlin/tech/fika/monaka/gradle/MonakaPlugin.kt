package tech.fika.monaka.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

class MonakaPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        val yamlExtension = target.extensions.create(
            "monakaYamlExport",
            MonakaExtension::class.java,
        )

        yamlExtension.outputDir.convention(
            target.layout.buildDirectory.dir("monaka-yaml")
        )

        yamlExtension.pumlOutputDir.convention(yamlExtension.outputDir)

        target.tasks.register(
            "generateMonakaYaml",
            GenerateStateMachineYamlTask::class.java,
        ) { task ->
            task.group = "monaka"
            task.description = "Generates YAML documentation from stateMachine { } DSL blocks."
            task.sources.setFrom(yamlExtension.sources)
            task.outputDir.set(yamlExtension.outputDir)
        }

        target.tasks.register(
            "generateMonakaPuml",
            GenerateStateMachinePumlTask::class.java,
        ) { task ->
            task.group = "monaka"
            task.description = "Generates PlantUML state diagrams from stateMachine { } DSL blocks."
            task.sources.setFrom(yamlExtension.sources)
            task.outputDir.set(yamlExtension.pumlOutputDir)
        }

        val stubsExtension = target.extensions.create(
            "monakaStubs",
            MonakaStubsExtension::class.java,
        )

        stubsExtension.input.convention(target.projectDir.absolutePath)
        stubsExtension.style.convention(StubStyle.CLASS)
        stubsExtension.replace.convention(false)
        stubsExtension.useTransitionAnnotation.convention(true)

        target.tasks.register(
            "generateMonakaStubs",
            GenerateStubsFromYamlTask::class.java,
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
