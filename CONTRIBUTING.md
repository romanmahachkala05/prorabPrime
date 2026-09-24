# Contributing

How to build, verify and change this codebase without breaking it.

- **What the architecture is** — [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
- **Why it is that way** — [`docs/DECISIONS.md`](docs/DECISIONS.md)

---

## The verification gate

A change is not done until the verification build passes and no test was lost.
Two gates, both real tasks in the root `build.gradle.kts`:

    ./gradlew verify           # every change, every time. No device needed.
    ./gradlew verifyOnDevice   # before merging into dev. Needs a device/emulator.

- **`verify`** = `:app:assembleDebug` + every subproject's own `check` task —
  ktlint, detekt, and the unit tests all attach themselves to `check`, so `verify`
  depends on `check` itself rather than naming module-specific task paths. A new
  module is wired into `verify` automatically. It also compiles every instrumented
  test APK, which catches a broken device test without a device.
- **`verifyOnDevice`** = `verify` + every module's instrumented tests (computed the
  same way, no hardcoded module list).

CI runs `verify` on every pull request and on pushes to `dev` and `main`. The local
command is deliberately the same one, so a red check can be reproduced without
translating a CI step back into Gradle tasks.

Start an emulator first (`emulator -avd <name>`, or from Android Studio);
instrumented tests fail with no device attached. **Keep the device awake** — a
screen that sleeps mid-run fails every Compose UI test in the module at once with
"No compose hierarchies found":

    adb shell input keyevent KEYCODE_WAKEUP
    adb shell svc power stayon true

Also useful:

| Command | Use |
| --- | --- |
| `./gradlew projects` | list every Gradle module |
| `./gradlew :app:dependencies --configuration debugRuntimeClasspath` | inspect the resolved graph |
| `./gradlew :server:run` | run the server locally (PostgreSQL from `docker compose up -d`) |

Build JDK: **17**, for both halves of the build. Compilation and tests use the Gradle
toolchain; the Gradle daemon itself uses the criteria in
`gradle/gradle-daemon-jvm.properties`, so `./gradlew` picks a JDK 17 daemon (downloading one
if the machine has none) whatever `JAVA_HOME` happens to be. Regenerate that file with
`./gradlew updateDaemonJvm --jvm-version=17`; do not hand-edit it.

---

## Where things live

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) §2b for the full dependency graph
and the rules behind it.

| Thing | Module | Location |
| --- | --- | --- |
| DTOs, enums, API path constants | `:api-contract` | `src/commonMain/kotlin/…/contract/` |
| Domain models, `AppError`, repository/service interfaces, use cases | `:core:domain` | `src/commonMain/kotlin/…/domain/` |
| Ktor client and its plugins, repository impls, mappers, error mapper, `dataModule` | `:core:data` | `src/commonMain/kotlin/…/data/` |
| Platform splits (HTTP engine, DataStore, image compression) | `:core:data` | `src/androidMain/`, `src/jvmMain/`, `src/jvmAndAndroidMain/` |
| ViewModel-facing primitives: `UiText`, `launchCatching`, `StateOwner`, `SnackbarNotifier` | `:core:ui` | `src/commonMain/kotlin/…/presentation/`, strings in `src/commonMain/composeResources/` |
| Theme, tokens, shared components | `:core:designsystem` | `src/commonMain/kotlin/…/presentation/theme/`, `…/components/` |
| Shared fakes, `MainDispatcherRule` | `:core:testing` | fakes in `src/commonMain/`, the rule in `src/jvmAndAndroidMain/` |
| One MVI screen (State/Event/StateHolder/VM/Screen/ErrorHandler) | `:feature:<name>` | `src/commonMain/kotlin/…/presentation/<screen>/`, strings in `src/commonMain/composeResources/` |
| Unit tests and JVM Compose UI tests | same module as the code they test | `src/jvmTest/kotlin/` (`src/androidHostTest/` for `androidMain` code) |
| Device tests | same module | `src/androidDeviceTest/kotlin/` |
| `App()`, `NavDisplay` + back stack, the Koin module list | `:shared` | `src/commonMain/kotlin/…/` |
| `Application`, `MainActivity` — the Android entry point only | `:app` | `src/main/kotlin/…/` |
| Server: routes, services, repositories, storage, migrations | `:server` | `src/main/kotlin/…/`, migrations in `src/main/resources/db/migration/` |
| Convention plugins (`prorab.kmp.library`, `.kmp.android.library`, `.kmp.compose`, `.koin`, `.quality`) | `build-logic` | `build-logic/convention/src/main/kotlin/` |
| Every dependency and version | — | `gradle/libs.versions.toml` |
| `verify` / `verifyOnDevice` | — | root `build.gradle.kts` |

Code lives under the `ru.prorabprime` package. A `:feature:*` module exposes only its
`NavKey` and one entry `@Composable`; everything else in it is `internal`.

---

## Local configuration

Nothing secret is committed.

- **Server:** copy `.env.example` to `.env` and fill it in; `application.conf` reads
  the environment.
- **App:** the default server URL and token come from `local.properties`
  (`prorab.serverUrl`, `prorab.apiToken`), which is gitignored. They can be changed at
  runtime in the app's settings screen.

---

## Do not change without saying so

- `gradle/libs.versions.toml` version bumps — call them out explicitly;
  never bump a version as a side effect of another change.
- `gradle/wrapper/`, `gradlew`, `gradlew.bat`.
- `build-logic/` — its convention plugins configure every module; a change
  here affects all of them at once.
- An applied Flyway migration — never edit it; add a new one.
- `.idea/` and generated `build/` directories.

---

## Conventions

- **New dependency** → add to `gradle/libs.versions.toml`, reference as `libs.…`.
  Never hardcode `"group:name:version"` in a build file. A dependency outside the
  stack in [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) §1 needs a stated reason first.
- **Screen status** is one sealed `UiStatus`, never `isXVisible` booleans.
- **No `var` state on a ViewModel** outside the `StateFlow` — model it in
  `XxxState`.
- **Screen arguments** do not come from `SavedStateHandle` (Navigation 3). Use
  Koin injected parameters — [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) §5.
- **Strings** live in each module's `composeResources/values/strings.xml`, grouped by
  screen and prefixed with its name. ViewModels and StateHolders use `UiText`.
- **Previews** render the stateless `XxxContent` with fake data, never
  `koinViewModel()`. One per screen, two at most.
- **Read-modify-write on a `MutableStateFlow` uses `update { }`**, never
  `state.value = f(state.value)`.
- **Every `onEvent` branch that calls a suspend or fallible operation handles
  errors the same way as its sibling branches** — through `launchCatching`, not a
  bare `runCatching`, which also catches `CancellationException` and reports leaving
  a screen mid-request as a failure.
- **A feature never depends on another feature.**
- **A `:feature:*` module's ViewModel, StateHolder, ErrorHandler and contracts
  are `internal`.** Only the `NavKey` and the entry `@Composable` are public.
  If a public `@Composable` needs to take the ViewModel as a parameter (for
  `koinViewModel()`'s default), split it into a public overload with no
  ViewModel parameter and a `private` one that takes it.
- **A module with a `@Serializable` type needs `kotlin.plugin.serialization`
  applied directly in its own `build.gradle.kts`.** Missing it compiles fine and
  crashes only at runtime, on first use of the type.
- **A branch on one value with a per-branch extra condition uses a subject
  `when` with a guard (`is X if cond -> …`)**, not `when { x is X && cond -> … }`.
- **Comments are short and rare.** One or two lines, only where the code cannot
  say it itself — a workaround, a constraint, a non-obvious ordering. Durable
  reasoning belongs in [`docs/DECISIONS.md`](docs/DECISIONS.md).
- **Language:** code, comments and docs in American English (`color`, `behavior`,
  `canceled`); user-facing strings in Russian.

---

## Commits

- One logical change per commit. Every commit builds and passes tests.
- **One-line message: a bracket tag, then a capitalized imperative summary. No
  body** — durable reasoning belongs in [`docs/DECISIONS.md`](docs/DECISIONS.md).

  | Tag | Use |
  | --- | --- |
  | `[TECH]` | **engineering-only work** — library/plugin upgrades, new deps, build tooling, the version catalog, the Gradle wrapper, tests, and documentation |
  | `[FEATURE]` | new or changed user-facing behavior |
  | `[FIX]` | bug fixes, and corrections to existing code — refactors, renames, cleanups |
  | `[MERGE]` | merge commits only |

  `[TECH]` is **not** a catch-all for "no user-facing change" — a refactor or
  rename that touches no dependency is `[FIX]`.

  Example: `[TECH] Add the api-contract module`

- No attribution trailers — no `Co-Authored-By:`, no "Generated with" line.
- Don't stage `.idea/`, `build/`, or anything listed in `.gitignore`.

---

## Branches

`main` holds releases; `dev` is the integration branch. Branch off `dev` — never
commit straight to it:

    git switch -c <prefix>/<short-kebab-name>

| Prefix | For | Commits |
| --- | --- | --- |
| `tech/` | dependencies, build tooling, tests, documentation | `[TECH]` |
| `feature/` | new or changed behavior | `[FEATURE]` |
| `fix/` | bug fixes, refactors, renames, cleanups | `[FIX]` |

One branch → one merge into `dev`.

**A branch's commits all match its prefix's tag.** If work of a different kind
turns up mid-branch — a real bug found while testing a `tech/` branch, say — it
does not go on the current branch. Branch off `dev` (or off the current tip if
it depends on unmerged work, then rebase once that merges).

**Review the branch diff before merging.** The verification build catches
compile errors and failing tests; it does not catch a misplaced side effect (UI
code in the data layer), an unhandled failure path, a main-thread blocking call,
or an `onEvent` branch guarded differently from its siblings.
