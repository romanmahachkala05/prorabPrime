import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

/** ktlint + detekt, identically configured in every module by the library convention plugins. */
class QualityConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("org.jlleitschuh.gradle.ktlint")
                apply("dev.detekt")
            }

            extensions.configure<KtlintExtension> {
                // Pinned, so a rule change arrives as a deliberate commit and not a red build.
                version.set(versionCatalog.findVersion("ktlintEngine").get().requiredVersion)
                // A formatting check that only warns is one nobody runs.
                ignoreFailures.set(false)
                reporters {
                    reporter(ReporterType.PLAIN)
                }
                filter {
                    // Generated sources are not ours to format. `invariantSeparatorsPath`,
                    // not `path`: on Windows the latter uses backslashes and never matches.
                    exclude { it.file.invariantSeparatorsPath.contains("/build/") }
                }
            }

            extensions.configure<DetektExtension> {
                // Only the deviations are in the file; the rest are detekt's defaults.
                buildUponDefaultConfig.set(true)
                config.setFrom(rootProject.file("config/detekt/detekt.yml"))
                // Neither androidTest nor the multiplatform source sets are in detekt's
                // defaults. Listed rather than globbed so a new source set is a deliberate
                // addition here, and filtered so JVM (`:server`, `:app`) and KMP modules can
                // share one list.
                source.setFrom(
                    listOf(
                        "src/main", "src/test", "src/androidTest",
                        "src/commonMain", "src/commonTest",
                        "src/androidMain", "src/androidHostTest", "src/androidDeviceTest",
                        "src/jvmMain", "src/jvmTest", "src/jvmAndAndroidMain",
                    ).map { project.file(it) }.filter { it.exists() },
                )
            }

            tasks.withType<Detekt>().configureEach {
                jvmTarget.set("17")
                reports {
                    html.required.set(true)
                    sarif.required.set(false)
                    markdown.required.set(false)
                }
            }
        }
    }
}
