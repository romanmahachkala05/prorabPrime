import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByName
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * For multiplatform modules that also ship an Android artifact: everything with platform code
 * or Compose UI.
 *
 * Uses `com.android.kotlin.multiplatform.library`, not `com.android.library`: AGP 9 refuses to
 * apply the latter alongside the multiplatform plugin. That plugin names its source sets
 * `androidMain`, `androidHostTest` and `androidDeviceTest` — not `main`/`test`/`androidTest`.
 *
 * `jvm()` joins the Android target so unit and Compose UI tests run on the JVM without a device.
 */
class KmpAndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("org.jetbrains.kotlin.multiplatform")
                apply("com.android.kotlin.multiplatform.library")
                apply("prorab.quality")
            }

            extensions.configure<KotlinMultiplatformExtension> {
                jvm()
                jvmAndAndroidHierarchy()
                jvmToolchain(17)

                extensions.getByName<KotlinMultiplatformAndroidLibraryTarget>("android").apply {
                    compileSdk = COMPILE_SDK
                    minSdk = MIN_SDK

                    // Off by default in this plugin, unlike `com.android.library`. A module
                    // with a `res/` directory needs it to get an `R` class at all.
                    androidResources.enable = true

                    withHostTest {
                        // Keeps android.util.Log, a JVM stub that throws, out of the way.
                        isReturnDefaultValues = true
                    }
                    withDeviceTest {
                        // Without this AGP falls back to the legacy runner, which discovers no
                        // JUnit4 tests: instrumented tests then run zero tests and report success.
                        instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                    }
                }
            }
        }
    }
}
