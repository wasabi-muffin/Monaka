import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.plugins.signing.SigningExtension

/**
 * Configures Maven publishing for publishable KMP library modules.
 *
 * Publishes to Maven Central via the Central Portal and mavenLocal().
 * Apply alongside `monaka.kmp.library` on modules that should be published.
 *
 * Required gradle.properties (or env vars):
 *   mavenCentralUsername      / ORG_GRADLE_PROJECT_mavenCentralUsername
 *   mavenCentralPassword      / ORG_GRADLE_PROJECT_mavenCentralPassword
 *   signingInMemoryKeyId      / ORG_GRADLE_PROJECT_signingInMemoryKeyId      (8-char key ID)
 *   signingInMemoryKeyPassword / ORG_GRADLE_PROJECT_signingInMemoryKeyPassword
 *   signingInMemoryKey        / ORG_GRADLE_PROJECT_signingInMemoryKey        (base64-encoded armored private key)
 *
 * To export the base64-encoded key:
 *   gpg --export-secret-keys --armor <KEY_ID> | grep -v '^[-=]' | tr -d '\n' | pbcopy
 */
class PublicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.vanniktech.maven.publish")
            pluginManager.apply("signing")

            group = Config.GROUP
            version = Config.VERSION

            extensions.configure<MavenPublishBaseExtension> {
                publishToMavenCentral()
                signAllPublications()

                coordinates(
                    groupId = group.toString(),
                    artifactId = name,
                    version = version.toString(),
                )

                pom {
                    name.set(project.name)
                    description.set("A Kotlin Multiplatform MVI based State Machine library.")
                    url.set("https://github.com/wasabi-muffin/Monaka")
                    inceptionYear.set("2026")

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
