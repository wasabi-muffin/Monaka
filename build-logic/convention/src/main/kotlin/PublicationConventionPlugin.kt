import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.kotlin.dsl.configure

/**
 * Adds Maven publishing to mavenLocal() for publishable KMP library modules.
 *
 * Sets group = "tech.fika" and version = "0.1.0".
 * Apply alongside `monaka.kmp.library` on modules that should be published.
 */
class PublicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("maven-publish")

            group = "tech.fika"
            version = "0.1.0"

            extensions.configure<PublishingExtension> {
                repositories {
                    mavenLocal()
                }
            }
        }
    }
}
