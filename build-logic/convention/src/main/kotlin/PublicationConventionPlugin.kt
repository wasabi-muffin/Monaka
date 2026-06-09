import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SonatypeHost
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Configures Maven publishing for publishable KMP library modules.
 *
 * Publishes to Maven Central via the Central Portal and mavenLocal().
 * Apply alongside `monaka.kmp.library` on modules that should be published.
 *
 * Required gradle.properties (or env vars):
 *   mavenCentralUsername / ORG_GRADLE_PROJECT_mavenCentralUsername
 *   mavenCentralPassword / ORG_GRADLE_PROJECT_mavenCentralPassword
 *   signing.keyId        / ORG_GRADLE_PROJECT_signingKeyId
 *   signing.password     / ORG_GRADLE_PROJECT_signingPassword
 *   signing.secretKeyRingFile or signing.key (in-memory, base64-encoded)
 */
class PublicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.vanniktech.maven.publish")

            group = Config.GROUP
            version = Config.VERSION

            extensions.configure<MavenPublishBaseExtension> {
                publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
                signAllPublications()

                coordinates(
                    groupId = group.toString(),
                    artifactId = name,
                    version = version.toString(),
                )

                pom {
                    name.set(project.name)
                    description.set("A Kotlin Multiplatform MVI based State Machine library.")
                    url.set("https://github.com/fika-tech/Monaka")
                    inceptionYear.set("2024")

                    licenses {
                        license {
                            name.set("Apache-2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0")
                            distribution.set("https://www.apache.org/licenses/LICENSE-2.0")
                        }
                    }

                    developers {
                        developer {
                            id.set("wasabi-muffin")
                            name.set("Marco Valentino")
                            url.set("https://github.com/wasabi-muffin")
                        }
                    }

                    scm {
                        url.set("https://github.com/wasabi-muffin/Monaka")
                        connection.set("scm:git:git://github.com/wasabi-muffin/Monaka.git")
                        developerConnection.set("scm:git:ssh://github.com/wasabi-muffin/Monaka.git")
                    }
                }
            }
        }
    }
}
