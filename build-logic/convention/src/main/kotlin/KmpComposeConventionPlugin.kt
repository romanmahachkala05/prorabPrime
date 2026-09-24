import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.compose.ComposePlugin
import org.jetbrains.compose.resources.ResourcesExtension
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Additive: applied alongside `prorab.kmp.android.library` by modules that render Compose UI.
 *
 * Strings and drawables live in `src/commonMain/composeResources/` and are read through a
 * generated `Res` class instead of `R`. Each module gets its own `Res`, in a package derived
 * from its Gradle path — `:core:ui` → `ru.prorabprime.core.ui.resources` — so two modules'
 * resources can never collide.
 */
class KmpComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("org.jetbrains.kotlin.plugin.compose")
                apply("org.jetbrains.compose")
            }

            configureComposeStability()
            configureComposeMetrics()

            extensions.configure<ComposeExtension> {
                extensions.configure<ResourcesExtension> {
                    packageOfResClass = "ru.prorabprime" + path.replace(':', '.') + ".resources"
                }
            }

            extensions.configure<KotlinMultiplatformExtension> {
                sourceSets.named("commonMain").configure {
                    dependencies {
                        implementation(library("compose-mp-runtime"))
                        implementation(library("compose-mp-foundation"))
                        implementation(library("compose-mp-ui"))
                        implementation(library("compose-mp-material3"))
                        implementation(library("compose-mp-resources"))
                        implementation(library("compose-mp-ui-tooling-preview"))
                    }
                }
                // JVM UI tests (`runComposeUiTest`) need what a desktop app has and a bare JVM
                // does not: Skia's native library for the host, and a `Dispatchers.Main`.
                // Without either a test fails to initialize rather than failing an assertion.
                sourceSets.matching { it.name == "jvmTest" }.configureEach {
                    dependencies {
                        implementation(library("compose-mp-ui-test"))
                        implementation(ComposePlugin.DesktopDependencies.currentOs)
                        implementation(library("kotlinx-coroutines-swing"))
                    }
                }
            }
        }
    }

    private fun Project.library(alias: String) = versionCatalog.findLibrary(alias).get()
}

/**
 * Declares stable the classes the compiler cannot work out for itself — a domain model in a
 * module it does not compile, and a third-party type nobody here can annotate.
 * Always on: this one changes what the compiler generates, not what it reports.
 */
private fun Project.configureComposeStability() {
    val config = rootProject.layout.projectDirectory.file("config/compose-stability.conf")
    extensions.configure<ComposeCompilerGradlePluginExtension> {
        stabilityConfigurationFiles.add(config)
    }
}

/**
 * Opt-in Compose compiler metrics: `./gradlew assembleRelease -Pprorab.composeMetrics` writes,
 * per module, which composables skip and which classes the compiler considers stable. Off by
 * default — it is diagnostic output no ordinary build reads.
 */
private fun Project.configureComposeMetrics() {
    if (!providers.gradleProperty(COMPOSE_METRICS_PROPERTY).isPresent) return

    // Each module's own build directory: the compiler takes this as a plugin option, and a
    // module path in the value carries a `:`, which is the option format's own separator.
    val destination = layout.buildDirectory.dir("compose-metrics")
    extensions.configure<ComposeCompilerGradlePluginExtension> {
        metricsDestination.set(destination)
        reportsDestination.set(destination)
    }
}

private const val COMPOSE_METRICS_PROPERTY = "prorab.composeMetrics"
