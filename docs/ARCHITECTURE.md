# Architecture Rules

These rules are mandatory. If a request conflicts with them, explain the conflict
before changing the architecture. This file is the *what*; `AGENTS.md` and
`CONTRIBUTING.md` are the *how to build / verify*; `DECISIONS.md` is the *why*.

Adapted from CatsListKMP v3.0.0. Where a rule is inherited unchanged, its original
reasoning is cited as **CatsListKMP ADR-NNNN**; decisions made for this project are
plain **ADR-NNNN** in [`DECISIONS.md`](DECISIONS.md).

---

## 1. Stack

- **Kotlin** (latest stable), **Compose Multiplatform** + Material 3,
  **Coroutines + Flow**, **kotlinx.serialization**, **kotlinx.collections.immutable**.
- Client: **Koin** for DI, **Navigation 3** (JetBrains builds of the UI) for navigation,
  **Ktor Client** for HTTP, **Coil 3** over the same Ktor client for images,
  **DataStore** for settings.
- Server: **Ktor** (Netty), **Koin** (koin-ktor), **Exposed**, **Flyway**,
  **PostgreSQL**, **HikariCP** (§11).
- **All dependency versions live in `gradle/libs.versions.toml`.** Never write a
  version string in a `build.gradle.kts`. Verify each version is the current
  stable before adding it; the catalog is the single source of truth. A pinned or
  held-back version carries a comment with the reason and a `Review when:`.

Targets: Android and JVM now; `wasmJs` joins the client modules in stage 3 (ADR-0004).
No iOS, no desktop app — the JVM target exists for the server and for running tests
without a device.

---

## 2. Architecture

### 2a. Why modules from the start

A project this small would normally start single-module (CatsListKMP ADR-0001). Here
the triggers for splitting exist on day one — a contract shared by server and client,
domain/data/UI reused by a web client, a known third feature. See ADR-0001.

### 2b. Module graph

Arrows = "depends on":

    :app                ──▶ :shared                                    (Android entry point only)
    :shared             ──▶ :feature:*, :core:data, :core:ui, :core:designsystem
    :feature:*          ──▶ :core:domain, :core:ui, :core:designsystem
    :core:designsystem  ──▶ :core:domain, :core:ui
    :core:ui            ──▶ :core:domain
    :core:data          ──▶ :core:domain, :api-contract
    :core:domain        ──▶ (nothing)
    :api-contract       ──▶ (nothing)
    :core:testing       ──▶ :core:domain, :core:ui                     (test-only; nothing depends on it in `main`)
    :server             ──▶ :api-contract                              (and nothing else from the client)

Every module but `:app` and `:server` is Kotlin Multiplatform. Source sets are
`commonMain` plus `androidMain`/`jvmMain`, with `jvmAndAndroidMain` for what only the
two JVM-based targets share (the OkHttp engine, JUnit rules). Tests are `commonTest`,
`androidHostTest` (JVM, no device), `androidDeviceTest` (instrumented) and `jvmTest` —
AGP's multiplatform plugin and Kotlin's default hierarchy name them, not us.

Rules:

- **`:core:domain` is pure Kotlin in `commonMain`**: models, `AppError`, repository
  and service interfaces (the ports), use cases. Only stdlib, coroutines and
  kotlinx.collections.immutable — no Android, Ktor, kotlinx.serialization or Koin,
  checked by construction (its convention plugin declares none of them). CatsListKMP
  keeps models in a separate `:core:model`; here they are one module (ADR-0002).
- **`:api-contract` holds only `@Serializable` DTOs, enums and path constants.** No
  logic. **DTOs never leave `:core:data` and `:server`** — features see domain
  models only.
- **No `java.*` and no platform API in `commonMain`** of any module: `wasmJs` is
  coming. Platform code goes in `androidMain`/`jvmMain`/`jvmAndAndroidMain`, and only
  where it is platform-specific by nature (image compression, DataStore, HTTP engine).
- IDs in the domain are value classes (`@JvmInline value class ObjectId(val value: String)`),
  so two kinds of ID cannot be swapped. Time is `kotlin.time.Instant`.
- **A `:feature:*` module MUST NOT depend on another `:feature:*` module.**
  Cross-feature navigation goes through `() -> Unit` callbacks (or a
  `Navigator` interface) wired by `:shared`; shared logic goes in `:core:*`.
- **Each `:feature:*` module exposes exactly two public things: its `NavKey`
  and one entry `@Composable`.** Everything else — ViewModel, StateHolder,
  ErrorHandler, `XxxContract`, the Koin `Module`, the stateless
  `XxxContent` — is `internal`, enforced by the compiler. A public
  `@Composable` cannot take an `internal` type as a parameter, so the public
  `XxxScreen(modifier, contentPadding)` delegates to a `private` overload that
  takes the `internal` ViewModel; that private overload is where
  `koinViewModel()`'s default lives.
- `:shared` is the **root UI**: `App()`, the `NavDisplay` and its back stack, and
  `appModules`, the Koin module list. `:app` is the Android entry point only:
  `Application` (starts Koin, builds Coil's `ImageLoader`, supplies `BuildConfig`
  defaults) and `MainActivity`. Neither holds screens, ViewModels, use cases or
  feature-specific DI modules.
- `:core:data` owns the repository implementations, the Ktor client and its plugins,
  the DTO↔domain mappers, the error mapper and the settings store.
- `:core:designsystem` (theme, tokens, shared components) and `:core:ui`
  (ViewModel-facing primitives — `UiText`, `launchCatching`, `StateOwner`,
  `SnackbarNotifier`) are deliberately separate: one is Compose-visual, the other is
  logic a ViewModel can use without pulling in a design system.
- Feature UI (screens + ViewModels) MUST NOT touch a repository directly — only
  use cases.
- Shared build config lives in the `build-logic` composite build as
  **convention plugins**: `prorab.kmp.library` (pure-common modules) and
  `prorab.kmp.android.library` (everything with an Android artifact) are the bases;
  `prorab.kmp.compose`, `prorab.koin` and `prorab.quality` (ktlint + detekt) are
  additive, applied only by the modules that need them. Never copy an `android { }`
  block between modules.
- **A module that declares a `@Serializable` type (a feature's `NavKey`, the DTOs)
  needs `kotlin.plugin.serialization` applied directly** — it does not come from any
  convention plugin. Missing it compiles cleanly and crashes only when the serializer
  is first looked up at runtime.

`api` vs `implementation`:

- Use `api` **only** for a dependency whose types appear in this module's
  *public* signatures. `:core:testing`'s fakes implement `:core:domain`'s ports and
  `:core:ui`'s `SnackbarNotifier`, so it exposes both as `api`.
- Everything else is `implementation`.

---

## 3. MVI — the screen contract

Every screen is a subpackage with these parts:

| File | Role |
| --- | --- |
| `XxxContract.kt` | `XxxState` (immutable), `XxxEvent` (sealed — all user intents) |
| `XxxStateHolder.kt` | `IXxxStateHolder` + impl — owns the `MutableStateFlow`, exposes intent-named mutators (§3a) |
| `XxxViewModel.kt` | thin orchestrator, registered in the feature's Koin module (§3b) |
| `XxxScreen.kt` | stateless `XxxContent(state, onEvent)` + thin `koinViewModel()` entry (§7) |
| `XxxErrorHandler.kt` | `IXxxErrorHandler` + impl — maps failures to state / notifications (§3c) |
| `XxxUiMapper.kt` *(if the screen renders a list/sections)* | domain model → UI model |
| `XxxDialogFactory.kt` *(if the screen shows dialogs)* | builds `DialogModel` from a sealed `DialogType` (§3d) |
| `XxxViewModelTest`, `XxxStateHolderTest`, mapper/factory tests | §9 |

### 3a. State + StateHolder

- `XxxState` is an **`@Immutable data class`** holding the *complete* UI state.
- **Screen status is ONE sealed type, never a set of `isXVisible` booleans:**

      @Immutable
      sealed interface UiStatus {
          data object Content : UiStatus
          data object Empty : UiStatus
          data object Loading : UiStatus
          data class Error(val message: UiText, val retryable: Boolean) : UiStatus
      }

- **Declare the statuses most-likely-first, and make every `when` over them
  mirror that order**, so branch order is checkable against the declaration.

      @Immutable
      data class XxxState(
          val status: UiStatus = UiStatus.Loading,
          val items: ImmutableList<XxxUiItem> = persistentListOf(),
          val dialog: DialogModel? = null,
          val pendingAction: XxxAction? = null,   // model in-flight ops here — NOT as a var on the VM
          val isRefreshing: Boolean = false,
      )

- **No mutable state outside the StateFlow.** Anything old-style code would keep as
  a `private var` on the ViewModel (a pending action, a cached list, a "which
  dialog") is a field in `XxxState`.
- The **StateHolder owns the flow and all mutation**:

      interface IXxxStateHolder : StateOwner<XxxState> {
          fun showLoading()
          fun showContent(items: ImmutableList<XxxUiItem>)
          fun showEmpty()
          fun showError(message: UiText, retryable: Boolean)
          fun setRefreshing(value: Boolean)
          fun setDialog(dialog: DialogModel?)
          fun setPendingAction(action: XxxAction?)
          fun reset()
      }

      class XxxStateHolder : IXxxStateHolder {
          private val _state = MutableStateFlow(XxxState())
          override val state: StateFlow<XxxState> = _state.asStateFlow()

          override fun showContent(items: ImmutableList<XxxUiItem>) = _state.update {
              it.copy(status = UiStatus.Content, items = items, isRefreshing = false)
          }
          // …one mutator per transition; each produces a valid full state
          override fun reset() = _state.update { XxxState() }
      }

- The ViewModel *and* every collaborator that mutates the state (error handler,
  etc.) share **one StateHolder instance**, built inside the `viewModel { }`
  definition (§6).
- The ViewModel re-exposes the state: `class XxxViewModel(...) : ViewModel(),
  StateOwner<XxxState> by stateHolder`.

### 3b. ViewModel — thin orchestrator

- Registered in the feature's Koin module, delegates state via `by stateHolder`,
  contains **no** state mutation and **no** error branching.
- One **typed event entry point**: `fun onEvent(event: XxxEvent)` over a sealed
  `XxxEvent`.
- On an event: call a use case, then a StateHolder mutator and/or a collaborator.
  Every branch that calls a suspend or fallible operation goes through
  `launchCatching`, never a bare `runCatching` (CatsListKMP ADR-0013).
- **Constructor dependency cap ≈ 7.** Consolidate collaborators: `navigator`,
  `notifier`, `errorHandler`, plus the use case(s) and the state holder.

      class XxxViewModel(
          private val stateHolder: IXxxStateHolder,
          private val errorHandler: IXxxErrorHandler,
          private val getItems: GetXxxUseCase,
          private val notifier: SnackbarNotifier,
          private val dialogFactory: IXxxDialogFactory,
      ) : ViewModel(), StateOwner<XxxState> by stateHolder {

          init { observeItems() }

          private fun observeItems() {
              getItems()                              // Flow<Result<List<Xxx>>>
                  .onEach(::render)
                  .launchIn(viewModelScope)           // set up ONCE, never re-launch
          }

          fun onEvent(event: XxxEvent) = when (event) {
              XxxEvent.Refresh          -> refresh()
              is XxxEvent.DeleteClicked -> confirmDelete(event.id)
              XxxEvent.DialogConfirmed  -> runPendingAction()
              XxxEvent.DialogDismissed  -> stateHolder.setDialog(null)
          }

          private fun render(result: Result<List<Xxx>>) = result
              .onSuccess { stateHolder.showContent(it.toUi()) }
              .onFailure { errorHandler.onLoadFailure(it) }
      }

- **No `viewModelScope.launch` nested inside a coroutine you are already in.**
- Transient state that must survive process death (a form draft, a
  `pendingAction` mid-dialog, the URI of a photo being taken) is persisted via
  **`SavedStateHandle`**.

### 3c. Extracted error handler

- All failure → UI mapping lives in `IXxxErrorHandler`. The ViewModel calls it;
  the handler calls StateHolder mutators + `notifier`.
- Branch on the sealed **`AppError`** from the domain (`Network`, `Unauthorized`,
  `NotFound`, `Validation`, `PhotoRejected`, `Server`, `Unknown`), never on raw exception
  classes. `Result` failures carry it wrapped in `AppErrorException`; unwrap with
  `Throwable.asAppError()`.
  Only `:core:data`'s error mapper knows what an HTTP status or an `IOException`
  means (CatsListKMP ADR-0032).

      class XxxErrorHandler(
          private val stateHolder: IXxxStateHolder,
          private val notifier: SnackbarNotifier,
      ) : IXxxErrorHandler {
          override fun onLoadFailure(error: AppError) = when (error) {
              is AppError.Network -> stateHolder.showError(UiText.Resource(Res.string.common_error_network), retryable = true)
              else                -> stateHolder.showError(UiText.Resource(Res.string.common_error_generic), retryable = true)
          }
      }

### 3d. Dialog factory + dialog-as-state

- Dialogs are data, not navigation. `XxxState.dialog: DialogModel?` (nullable
  field). The Composable renders it when non-null.
- `DialogModel` is an `@Immutable` sealed type; text is `UiText`.
- `IXxxDialogFactory` builds a `DialogModel` from a sealed `XxxDialogType`. The
  ViewModel: `stateHolder.setDialog(dialogFactory.confirmDelete())`.
- Confirm/dismiss come back as `XxxEvent`s; the pending operation to run on
  confirm is stored in `XxxState.pendingAction`.

---

## 4. Coroutines & Flow

- `viewModelScope` only. Expose state as `MutableStateFlow` → `asStateFlow()`.
- Collect upstream with `flow.onEach { }.catch { }.launchIn(scope)` — **set up
  once**; never re-`launchIn` the same source on a repeated call.
- Domain / data `suspend` functions are **main-safe**. Blocking work (file I/O,
  JDBC, image decoding) runs on an **injected `CoroutineDispatcher`** (a named Koin
  definition), never a hardcoded `Dispatchers.IO`, so tests can substitute it.
- **Read-modify-write on a `MutableStateFlow` uses `update { }`**, never
  `state.value = f(state.value)`. `update` is a compare-and-set loop; the plain form
  reads and writes separately. Across a suspension point, atomicity needs a `Mutex`
  held across the whole operation or a database transaction
  (CatsListKMP ADR-0015).
- Recoverable failures are returned as `Result<T>`, not thrown across the domain
  boundary. `CancellationException` is always rethrown, never wrapped.
- One-shot effects (navigation, snackbars) go through injected collaborators
  (`Navigator`, `SnackbarNotifier`). Do not put one-shot signals in `State`.
- **No second event bus** for "another screen wants this one to refresh." Observe
  a `Flow` from the data layer (single source of truth) instead. When the source is
  a server query, the repository owns an invalidation flow and every write that can
  change a query's result bumps it.

---

## 5. Navigation 3

- Each feature owns its `NavKey` (`@Serializable data object` / `data class`) in
  an `XxxNavKey.kt`.
- The `NavDisplay` + back stack lives in `:shared` — the only place that knows
  every feature's key.
- Screens take `onNavigateX: () -> Unit` callbacks (or an injected `Navigator`
  whose impl is in `:shared`). A feature never references another feature's key
  and never builds the graph.
- **Screen arguments** — Navigation 3 does **not** route args through
  `SavedStateHandle`. Use Koin's injected parameters:

      class XxxViewModel(
          private val id: ObjectId,
          …
      ) : ViewModel()

      // in the feature's Koin module:
      viewModel { (id: ObjectId) -> XxxViewModel(id = id, …) }

      // in the NavDisplay entry:
      val vm = koinViewModel<XxxViewModel> { parametersOf(ObjectId(key.id)) }

  Pass a plain value, not the `NavKey` type, so the ViewModel stays free of
  navigation types.
- `SavedStateHandle` is for surviving **process death** of transient state
  (drafts, `pendingAction`), never for route args.

---

## 6. Koin

- Each module owns its own DI as a `Module` value — `dataModule`, `uiModule`,
  `objectsModule`, `settingsModule`. Koin does **not** aggregate them: `appModules`
  in `:shared` lists every one by hand, and a new module must be added there or
  nothing in it resolves.
- **`:core:domain` carries no DI at all.** Use cases are plain classes with plain
  constructors; `dataModule` is what knows how to build them.
- `single` for app-wide objects (repositories, the `HttpClient`, the settings store,
  the `SnackbarNotifier`); `factory` for everything else.
- Screen collaborators that must **share** a StateHolder (ViewModel + ErrorHandler)
  are constructed **inside the `viewModel { }` lambda** and passed to both. Do not
  register a StateHolder as its own definition, or the two will resolve to
  different instances.
- Apply Koin through the `prorab.koin` convention plugin, never by hand per module.
  The server is the exception: it uses koin-ktor directly (§11).
- **Every feature module gets a graph test** (`ObjectsModuleTest`) that resolves its
  ViewModels against `:core:testing` fakes. Koin resolves at runtime, so this is
  what catches a definition that drifted from its constructor (CatsListKMP ADR-0026).

---

## 7. Compose / UI

- Split every screen: a **stateless** `XxxContent(state: XxxState, onEvent: (XxxEvent) -> Unit)`
  plus a thin `XxxScreen(viewModel: XxxViewModel = koinViewModel())` that does
  `val state by viewModel.state.collectAsStateWithLifecycle()` and forwards
  `viewModel::onEvent`.
- Composables contain **no business logic** — render state, emit events. Debounce,
  retries and similar timing logic live in the ViewModel as Flow operators.
- `@Immutable` / `@Stable` on `State` and every UI model type. Use
  `ImmutableList` / `persistentListOf()` (kotlinx.collections.immutable) in state,
  never a raw `List` you rebuild each emission.
- **Strong skipping is on, so most manual annotation is obsolete — but not all of
  it.** Two cases the compiler cannot work out on its own:
  - a **sealed interface** used as a parameter type is unstable unless the
    interface itself is `@Immutable` — and that promise is only true if every
    case is really immutable (`UiText.Resource` holds an `ImmutableList`, not a
    `List`, for exactly this reason);
  - a class from a module the **Compose compiler does not compile**
    (`:core:domain`) or from a **third-party library**. These are declared in
    [`config/compose-stability.conf`](../config/compose-stability.conf), never by
    moving the class or wrapping it.
- **Measure, don't guess.** `./gradlew assembleRelease -Pprorab.composeMetrics`
  writes the compiler's stability and skippability reports to each module's
  `build/compose-metrics/` (CatsListKMP ADR-0033).
- **Design system** lives in `:core:designsystem`: theme + tokens + reusable
  components (cards, status chip, loaders, error block, empty state, dialog host).
  Screens compose these; they don't hand-roll spacing/colors.

---

## 8. Strings & resources

- **No user-facing string literal in code.** UI text is Russian; code, comments and
  docs are English.
- Model text as a `UiText` sealed type, resolved only in Composables:

      @Immutable
      sealed interface UiText {
          data class Raw(val value: String) : UiText
          data class Resource(val id: StringResource, val args: ImmutableList<Any>) : UiText
      }
      @Composable fun UiText.resolve(): String = when (this) { … }
      suspend fun UiText.load(): String = when (this) { … }  // outside composition

  Strings are Compose resources (`src/commonMain/composeResources/values/strings.xml`),
  read through each module's generated `Res`.
- ViewModels / StateHolders / mappers **never** resolve strings — they put a
  `UiText` in state.
- Formatting / pluralization via string resources with placeholders (`%1$s`) or
  `plurals`, never string concatenation.
- **`strings.xml` is grouped by screen**, one comment-headed block per screen,
  in the order the screens appear in the app. Strings used by 2+ screens get their
  own `common` block.
- **Every string name is prefixed with its screen**: `objectslist_*`,
  `objectdetails_*`, `objectedit_*`, `settings_*`, `common_*` for shared strings.
  Truly app-wide strings with no screen owner (`app_name`) stay unprefixed.

---

## 9. Testing

Every use case, repository impl, mapper/factory, **StateHolder**, and
**ViewModel** has a unit test, in the same module, same package.

- **Where tests live** (CatsListKMP ADR-0039): unit tests in `jvmTest`, or
  `androidHostTest` for code in `androidMain`; Compose UI tests of a stateless
  `XxxContent` in `jvmTest` with `runComposeUiTest`; tests that need a real device
  (camera, FileProvider, DataStore on Android) in `androidDeviceTest`.
- **Hand-written fakes, not a mocking library.** A fake is a real in-memory
  implementation backed by `MutableStateFlow`. Shared fakes live in
  `:core:testing` (`commonMain`); `MainDispatcherRule` lives in its
  `jvmAndAndroidMain`. Screen-only fakes stay in that module's tests.
- ViewModel tests: `@get:Rule val mainDispatcherRule = MainDispatcherRule()`
  (`UnconfinedTestDispatcher`), build the real ViewModel with fake collaborators
  + real use cases over a fake repository, then assert on
  `viewModel.state.value` after calling `onEvent(...)`. Never involve Koin.
- `runTest { }` for anything touching `suspend` / `Flow`. Assertions with Google
  Truth.
- StateHolder tests assert each mutator produces a fully valid state (no
  half-set status).
- **Every screen gets a Compose UI test**: the stateless `XxxContent` with fake
  state, one test per branch, plus the events its controls send. A ViewModel test
  proves which status the screen reaches; only this proves what that status puts
  on screen.
- `:core:data` repositories are tested against Ktor's `MockEngine`.
- Server tests: §11.
- **A bug is reproduced by a failing test before it is fixed.**

---

## 10. Previews

- **1 preview per screen (max 2)** — e.g. `Content`, and one of `Error` /
  `dark`. Use `@PreviewParameter` with a small provider, or two `@Preview` funcs.
- **Every reusable design-system component** gets a preview.
- Previews render the **stateless** `XxxContent` with fake state + no-op
  `onEvent`; never `koinViewModel()`, never DI.
- Wrap every preview in the app theme.

---

## 11. Server

The server has no counterpart in CatsListKMP (ADR-0005). It depends on
`:api-contract` and nothing else from the client.

    server/src/main/kotlin/.../
      Application.kt   — module(): plugins, Koin, routes
      config/          — AppConfig, read from application.conf + environment
      routes/          — thin: parse the request → call a service → map to a DTO
      service/         — business rules (cover logic, validation)
      repository/      — interfaces + Exposed implementations
      storage/         — FileStorage interface + LocalFileStorage (disk) + thumbnails
      error/           — sealed ServiceError, mapped to HTTP in one place (StatusPages)
      di/              — Koin modules

Rules:

- **Routes hold no business logic and never touch Exposed** — only services.
- **Services depend on interfaces** (`ObjectRepository`, `PhotoRepository`,
  `FileStorage`) and are unit-tested with hand-written fakes.
- **An operation that touches both the database and the disk never leaves a
  dangling reference:**
  - *upload*: write the file and thumbnail first, then the row; if the row fails,
    delete the files;
  - *delete*: delete the row first, then the files; a failed file delete is logged,
    not surfaced (an orphan file is harmless, a row pointing at a missing file is not).

  The order and the compensation are covered by tests.
- **Errors:** services return `Result` / a sealed `ServiceError` (`NotFound`,
  `Validation`, `UnsupportedMedia`, `TooLarge`, …). They become HTTP statuses and an
  `ErrorDto { code, message }` in exactly one place: StatusPages.
- **Blocking calls** (JDBC, file I/O, image decoding) run on an injected dispatcher.
  Database work goes through `DbExecutor.query { }`: Exposed's `suspendTransaction` inside
  `withContext(dispatcher)` (`newSuspendedTransaction` is deprecated in Exposed 1.x).
- **Several repository writes that belong together run in `Transactor.inTransaction { }`**
  (implemented by `DbExecutor`); a repository call inside it joins that transaction. Services
  get the `Transactor`, not Exposed, so fakes can stand in for it.
- **Routes unwrap service results with `getOrThrow()`**; a `ServiceException` carries the
  `ServiceError` to StatusPages. Anything else thrown is a 500 whose message stays in the log.
- **Configuration** comes from `application.conf` overridden by environment
  variables. Nothing is hardcoded; secrets live in `.env`, never in git.
- **Auth:** every endpoint except `/health` requires `Authorization: Bearer <API_TOKEN>`;
  the token is compared in constant time (`MessageDigest.isEqual`).
- **Files** are served only under auth, with the requested path resolved and checked
  to stay inside `STORAGE_DIR` (no path traversal). Upload type is checked by the
  file's signature, not only its declared content type.
- **Schema changes only through Flyway migrations** (`V1__init.sql`, …). An applied
  migration is never edited — a fix is a new migration.
- **Tests:** services — unit tests with fakes (cover rules, compensation on file and
  database failures); routes — Ktor `testApplication` (status codes, 401 without a
  token, error format, path traversal); repositories — integration tests against
  PostgreSQL via Testcontainers, falling back to embedded PostgreSQL without Docker
  (ADR-0006, ADR-0007).

---

## 12. Anti-patterns — do NOT

- `isLoadingVisible` / `isErrorVisible` / `isEmptyVisible` boolean soup → one
  sealed `UiStatus`.
- A `private var` on the ViewModel holding state outside the `StateFlow` → model
  it as a `State` field.
- A second event bus / `Channel` for cross-screen "refresh" triggers → observe a
  data-layer `Flow`.
- `viewModelScope.launch { }` nested inside a coroutine you're already in.
- Re-`launchIn`-ing the same upstream flow on a repeated call.
- Resolving a string in a ViewModel / StateHolder / mapper → `UiText`.
- Repository access from a Composable or ViewModel → use cases only.
- A DTO outside `:core:data` or `:server` → map it to a domain model.
- `java.*` or a platform API in `commonMain`.
- A `:feature:*` module depending on another `:feature:*` module.
- A hardcoded dependency version in a build file → version catalog.
- More than ~7 constructor parameters on a ViewModel → consolidate collaborators.
- Repository interfaces or use cases outside `:core:domain`.
- Business logic or Exposed calls in a server route → a service.
- A UI-producing side effect (`Snackbar`, `Dialog`) triggered from `data` or
  `domain` → return a result; the ViewModel decides what the user sees.

---

## 13. Before writing code

1. Read this file, `AGENTS.md` and `CONTRIBUTING.md`.
2. Inspect the existing structure; reuse existing abstractions.
3. Do not duplicate a repository, use case, state, event, mapper or ViewModel
   that already exists.
4. If the request conflicts with these rules, explain the conflict before
   changing the architecture.
