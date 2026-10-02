import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

/**
 * The root `libs.versions.toml` catalog, for convention plugin code to read. Not named `libs`:
 * that would silently shadow Gradle's generated accessor in every module applying the plugin.
 */
val Project.versionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")
