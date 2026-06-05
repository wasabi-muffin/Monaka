package tech.fika.monaka.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

class MonakaPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        val extension = target.extensions.create(
            "monakaYamlExport",
            MonakaExtension::class.java,
        )

        extension.outputDir.convention(
            target.layout.buildDirectory.dir("monaka-yaml")
        )

        target.tasks.register(
            "generateMonakaYaml",
            GenerateStateMachineYamlTask::class.java,
        ) { task ->
            task.group = "monaka"
            task.description = "Generates YAML documentation from stateMachine { } DSL blocks."
            task.sources.setFrom(extension.sources)
            task.outputDir.set(extension.outputDir)
        }
    }
}
