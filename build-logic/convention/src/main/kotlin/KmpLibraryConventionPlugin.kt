import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * For pure `commonMain` modules with no platform code (`:core:domain`, `:api-contract`).
 *
 * `jvm()` is what the server consumes directly and an Android consumer resolves; `wasmJs()` is
 * for the web client (ADR-0004, ADR-0016).
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("org.jetbrains.kotlin.multiplatform")
                apply("prorab.quality")
            }

            extensions.configure<KotlinMultiplatformExtension> {
                jvm()
                // Compiled only: tests run on the JVM target, so the browser's test task is off.
                wasmJs { browser { testTask { enabled = false } } }
                // An Android consumer resolves the `jvm` variant of these modules, so the
                // bytecode level has to match what AGP compiles the rest of the app to.
                jvmToolchain(17)
            }
        }
    }
}
