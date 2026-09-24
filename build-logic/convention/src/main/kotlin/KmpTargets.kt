import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

internal const val COMPILE_SDK = 37
internal const val MIN_SDK = 26

/**
 * The default hierarchy plus `jvmAndAndroid`: code for the two JVM-based targets only — the
 * OkHttp engine, JUnit rules. Declared in every module so a source set by that name means the
 * same thing wherever it appears.
 */
@OptIn(ExperimentalKotlinGradlePluginApi::class)
internal fun KotlinMultiplatformExtension.jvmAndAndroidHierarchy() {
    applyDefaultHierarchyTemplate {
        common {
            group("jvmAndAndroid") {
                withJvm()
                withCompilations { it.platformType == KotlinPlatformType.androidJvm }
            }
        }
    }
}
