# Decision record

[`ARCHITECTURE.md`](ARCHITECTURE.md) says *what* this codebase does. This file says *why*, and what
was given up for it.

Each entry follows the ADR shape — **Context**, **Decision**, **Alternatives rejected**,
**Consequences**, **Review when**. Entries are append-only: a decision that turns out to be
wrong is not edited, it is **superseded** by a later one, so the reasoning stays legible in
both directions. A decision whose core still holds but was written too broadly is **amended**
instead.

**What belongs here.** If another engineer could reasonably make a different choice without
knowing why this one was made, it is an ADR. If the answer is obvious from the code, it is a
comment.

The architecture is adapted from CatsListKMP v3.0.0. Its decision record is not copied; where
a rule here rests on one of its entries, the entry is cited as **CatsListKMP ADR-NNNN**. The
entries below are the points where this project departs from it or goes beyond it.

| # | Decision | Status |
| --- | --- | --- |
| [0001](#adr-0001) | Gradle modules from the first commit | Accepted |
| [0002](#adr-0002) | One `:core:domain`, no separate `:core:model` | Accepted |
| [0003](#adr-0003) | Coil shares the app's Ktor `HttpClient` | Accepted |
| [0004](#adr-0004) | Android + JVM now, `wasmJs` in stage 3; no iOS | Accepted |
| [0005](#adr-0005) | A Ktor server in the same repository | Accepted |
| [0006](#adr-0006) | PostgreSQL tests run in `verify`, skip without Docker, never skip on CI | Accepted |

---

## ADR-0001

### Gradle modules from the first commit

**Accepted** · 2026-09-25

**Context.** CatsListKMP started single-module and split only when a trigger appeared
(CatsListKMP ADR-0001, ADR-0022); its architecture guide recommends the same for any fresh
project with two screens. This project's triggers are already present on day one:
`:api-contract` has two consumers (the server and the client) that must not share anything
else; `:core:domain` and `:core:data` will be reused by a web client in stage 3; there are two
features from the start (objects, settings) and a third (receipts) is planned.

**Decision.** Start with the module graph in ARCHITECTURE.md §2b: `:api-contract`, `:server`,
`:core:domain`, `:core:data`, `:core:ui`, `:core:designsystem`, `:core:testing`,
`:feature:objects`, `:feature:settings`, `:shared`, `:app`.

**Alternatives rejected.**
- *Single client module plus the server.* The contract would have to live in the client or
  be duplicated on the server, and the boundary that keeps DTOs out of the UI would be a
  convention instead of a compile error.
- *Split later, as CatsListKMP did.* There the split was a migration of its own
  (CatsListKMP ADR-0022); here the eventual graph is already known.

**Consequences.** More build files and convention plugins from the start. Layering is enforced
by the compiler. `verify` wires new modules in automatically, so the extra modules cost no
upkeep in the gate.

**Review when:** never on its own — only if a module turns out to have a single consumer and
no prospect of another.

---

## ADR-0002

### One `:core:domain`, no separate `:core:model`

**Accepted** · 2026-09-25

**Context.** CatsListKMP keeps its domain models (and `AppError`) in `:core:model`, and ports
and use cases in `:core:domain`, which depends on it. There `:core:designsystem` depends on
the model without the use cases.

**Decision.** Models, `AppError`, ports and use cases live together in `:core:domain`, pure
Kotlin in `commonMain`, with only stdlib, coroutines and kotlinx.collections.immutable on its
classpath.

**Alternatives rejected.**
- *Copy the `:core:model` / `:core:domain` split.* Every consumer here needs both: features
  call use cases and render models, fakes implement ports over models. A second module would
  add a build file and an edge without keeping anything apart.

**Consequences.** `:core:designsystem` sees use cases it has no reason to call; that is a
review concern, not a compile error. Domain models are not compiled by the Compose compiler,
so the ones rendered by screens are listed in `config/compose-stability.conf`.

**Review when:** a module needs the models but must be kept from the use cases.

---

## ADR-0003

### Coil shares the app's Ktor `HttpClient`

**Accepted** · 2026-09-25

**Context.** CatsListKMP shares one `OkHttpClient` between Ktor's OkHttp engine and
`coil-network-okhttp` (CatsListKMP ADR-0030). Here images are served by our own server under
`/files/...`: every image request needs the same bearer token as the API, and its base URL can
change at runtime in the settings screen. The API responses carry relative file URLs for the
same reason.

**Decision.** Coil loads through `coil-network-ktor3` with the same `HttpClient` the API uses.
A small client plugin in `:core:data` reads the base URL and token from `SettingsRepository`
on every request and applies both, so API calls and image loads resolve relative URLs and
authenticate identically. The `ImageLoader` is built in `:app`'s `Application`, as in
CatsListKMP.

**Alternatives rejected.**
- *Share the `OkHttpClient` (CatsListKMP ADR-0030).* Shares the connection pool but not the
  Ktor plugins: token and base-URL handling would need a second implementation as an OkHttp
  interceptor, and none of it would exist for `wasmJs`, which has no OkHttp.
- *Ktor's `DefaultRequest` / `Auth` bearer plugins.* `DefaultRequest` fixes the base URL when
  the client is built; `Auth` caches the token. Both break "change the server without a
  restart".
- *Absolute URLs from the server.* The server does not know the address the phone reaches it
  by, and cached absolute URLs go stale when the address changes.

**Consequences.** One place decides how every request is addressed and authenticated.
The domain carries relative paths only. OkHttp is no longer pinned in the catalog: Ktor's
engine is its only consumer.

**Review when:** images move to a public CDN or another host with different auth.

---

## ADR-0004

### Android + JVM now, `wasmJs` in stage 3; no iOS

**Accepted** · 2026-09-25

**Context.** Stage 3 is a web client in Compose Multiplatform. CatsListKMP targets Android,
JVM desktop and iOS, and holds Kotlin at 2.3 and Coil at 3.4 because newer Coil iOS klibs use
Kotlin 2.4's ABI. Adopting its Compose Multiplatform layout (UI modules in `commonMain`, root
UI in `:shared`) only helps stage 3 if the UI stack also runs on `wasmJs`.

Checked on Maven Central and Google Maven on 2026-09-25 — each publishes a `wasm-js`
variant: `org.jetbrains.androidx.navigation3:navigation3-ui` (1.1.x stable line),
`androidx.navigation3:navigation3-runtime`, `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose`
and `lifecycle-viewmodel-navigation3`, `io.insert-koin:koin-core`/`koin-compose`/`koin-compose-viewmodel`
4.2.2, `io.coil-kt.coil3:coil-compose`/`coil-network-ktor3` 3.6.3, `io.ktor:ktor-client-core`
3.6.0, kotlinx-serialization, kotlinx-collections-immutable, Compose Multiplatform
`components-resources` and `material3`.

**Decision.** Client KMP modules target Android and JVM now; the JVM target serves the server
(for `:api-contract`) and device-free tests. `wasmJs()` is added to the convention plugins in
stage 3. No iOS or desktop app target. With iOS gone, the catalog takes Kotlin 2.4.x and the
current Coil.

**Alternatives rejected.**
- *Android-only UI modules, as the first draft of the plan had.* The web client would have to
  re-implement every screen.
- *Add `wasmJs` now.* Nothing consumes it in stage 1, and it would make every module compile a
  third target for no running code.

**Consequences.** `commonMain` must stay free of `java.*` and platform APIs from the start.
Platform-bound pieces (DataStore, image compression, the OkHttp engine) sit behind domain
interfaces with `androidMain` implementations and will need `wasmJsMain` ones in stage 3.

**Review when:** stage 3 starts — re-check the versions above, then add the target.

---

## ADR-0005

### A Ktor server in the same repository

**Accepted** · 2026-09-25

**Context.** CatsListKMP is a client for a public API; this project owns its backend. Stage 1
runs the server on the developer's machine; later it moves to a VPS.

**Decision.** `:server` is a JVM module in this repository — Ktor (Netty), Koin (koin-ktor),
Exposed, Flyway, PostgreSQL, HikariCP — with its own convention plugin, `prorab.ktor.server`.
It depends on `:api-contract` and nothing else from the client. Its layering (routes →
services → repositories/storage, errors mapped once in StatusPages) is ARCHITECTURE.md §11.

**Alternatives rejected.**
- *A separate repository for the server.* The DTOs would have to be published as an artifact
  or duplicated, and a contract change would span two pull requests.
- *A backend-as-a-service.* Receipts and analytics (stages 2 and 4) need server-side logic
  and SQL; photo storage would be tied to a vendor.

**Consequences.** One `verify` covers both sides of the contract. The server's integration
tests need Docker (Testcontainers).

**Review when:** the server gets a second, independent client or its own release cadence.

---

## ADR-0006

### PostgreSQL tests run in `verify`, skip without Docker, never skip on CI

**Accepted** · 2026-09-25

**Context.** The server's repositories and migrations are tested against a real PostgreSQL
started by Testcontainers — the schema relies on PostgreSQL-specific behavior (a composite
foreign key with `ON DELETE SET NULL (column)`, `JSONB`) that no in-memory database reproduces.
Testcontainers needs Docker. The CI runner has it; the development machine currently does not.

**Decision.** The integration tests are ordinary tests in `:server:test`, so `verify` runs them.
Each one first calls `TestPostgres.assumeAvailable()`: without Docker the test is skipped with
the reason in the report ("Docker is not available…"), and Gradle lists it as `SKIPPED`. When the
`CI` environment variable is set, a missing Docker fails the test instead.

**Alternatives rejected.**
- *A separate task, like `verifyOnDevice`.* A gate nobody is forced to run is the one that
  stops being run; CI would need a second job to cover it.
- *Always require Docker.* `verify` would be red on a machine that cannot run it, for reasons
  unrelated to the change.
- *H2 in PostgreSQL mode.* Does not implement the partial `SET NULL` or `JSONB`; a green test
  would prove nothing about the real schema.

**Consequences.** A local green `verify` without Docker does not cover the database; CI does.
Anything touching SQL is not done until CI has run it.

**Review when:** Docker is installed on every development machine — then the skip can go.
