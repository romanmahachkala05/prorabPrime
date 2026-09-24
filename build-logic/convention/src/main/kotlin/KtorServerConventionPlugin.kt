import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaApplication
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/**
 * For `:server`: a Kotlin/JVM Ktor application on Netty. Configuration is read by
 * `EngineMain` from `application.conf`, so the port and host come from there, not code.
 * The Ktor Gradle plugin adds `buildFatJar` for the Docker image.
 */
class KtorServerConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("org.jetbrains.kotlin.jvm")
                apply("application")
                apply("io.ktor.plugin")
                apply("prorab.quality")
            }

            extensions.configure<KotlinJvmProjectExtension> {
                jvmToolchain(17)
            }

            extensions.configure<JavaApplication> {
                mainClass.set("io.ktor.server.netty.EngineMain")
            }

            dependencies {
                add("implementation", library("ktor-server-core"))
                add("implementation", library("ktor-server-netty"))
                add("implementation", library("logback-classic"))

                add("testImplementation", library("ktor-server-test-host"))
                add("testImplementation", library("junit"))
                add("testImplementation", library("truth"))
                add("testImplementation", library("kotlinx-coroutines-test"))
            }
        }
    }

    private fun Project.library(alias: String) = versionCatalog.findLibrary(alias).get()
}
