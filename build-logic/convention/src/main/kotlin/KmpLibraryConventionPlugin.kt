import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * For pure `commonMain` modules with no platform code (`:core:domain`, `:api-contract`).
 *
 * `jvm()` is the only target for now: the server consumes it directly, and an Android consumer
 * resolves the same variant. `wasmJs()` joins in stage 3 (ADR-0004).
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
                // An Android consumer resolves the `jvm` variant of these modules, so the
                // bytecode level has to match what AGP compiles the rest of the app to.
                jvmToolchain(17)
            }
        }
    }
}
