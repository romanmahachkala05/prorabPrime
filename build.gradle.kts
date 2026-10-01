// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    // Provides `clean`; the Kotlin/Wasm Node setup applies it to the root project, so a hand-made one would clash.
    base
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ktor) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
}

// The Kotlin/Wasm tooling installs Karma, the browser test runner, from a fork hosted on GitHub; no
// test here runs in a browser, so a release from the npm registry does as well and needs no GitHub.
plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsRootPlugin> {
    extensions.configure<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsRootExtension> {
        versions.karma.version = "6.4.4"
    }
}

/** A module with instrumented tests, and the task suffixes that build and run them. */
data class InstrumentedTestModule(val project: Project, val assembleTask: String, val connectedTask: String)

// `include(":core:data")` also creates an unbuildable ":core" grouping project.
val buildableSubprojects = subprojects.filter { it.buildFile.exists() }

// `com.android.application` names them `androidTest`; the Kotlin Multiplatform Android library
// plugin names the same thing `androidDeviceTest`.
val androidTestModules = buildableSubprojects.flatMap { project ->
    buildList {
        if (project.projectDir.resolve("src/androidTest").exists()) {
            add(InstrumentedTestModule(project, "assembleDebugAndroidTest", "connectedDebugAndroidTest"))
        }
        if (project.projectDir.resolve("src/androidDeviceTest").exists()) {
            add(InstrumentedTestModule(project, "assembleAndroidDeviceTest", "connectedAndroidDeviceTest"))
        }
    }
}

/**
 * The gate every change has to pass; needs no device. Depends on each subproject's own `check`
 * rather than named task paths, so a new module is wired in without touching this.
 */
tasks.register("verify") {
    group = "verification"
    description = "Checks formatting and static analysis, assembles the debug APK and every instrumented test APK, builds the web site, runs every unit test in every module. No device needed."
    findProject(":app")?.let { dependsOn("${it.path}:assembleDebug") }
    dependsOn(buildableSubprojects.map { "${it.path}:check" })
    // The web client: every module's `wasmJs` target compiles (a `check` does not build it, as no
    // test runs there), and the site itself builds.
    dependsOn(provider { buildableSubprojects.mapNotNull { it.tasks.findByName("compileKotlinWasmJs")?.path } })
    findProject(":web")?.let { dependsOn("${it.path}:wasmJsBrowserDistribution") }
    // Instrumented tests cannot run without a device, but they compile without one.
    dependsOn(androidTestModules.map { "${it.project.path}:${it.assembleTask}" })
}

/** The gate before merging into `dev`: adds the instrumented tests, which need a device. */
tasks.register("verifyOnDevice") {
    group = "verification"
    description = "Everything in `verify`, plus every module's instrumented tests. Needs a device."
    dependsOn("verify")
    dependsOn(androidTestModules.map { "${it.project.path}:${it.connectedTask}" })
}
