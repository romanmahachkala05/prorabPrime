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
| [0006](#adr-0006) | PostgreSQL tests run in `verify`, skip without Docker, never skip on CI | Accepted, **amended** by 0007 |
| [0007](#adr-0007) | Embedded PostgreSQL when Docker is missing | Accepted |
| [0008](#adr-0008) | Requests go to a placeholder host, resolved per request | Accepted |
| [0009](#adr-0009) | Ask for local network access at startup | Accepted |
| [0010](#adr-0010) | Extra contacts are a sub-resource; the primary client stays on the object | Accepted |
| [0011](#adr-0011) | Photos and receipts are one table with a kind | Accepted |
| [0012](#adr-0012) | Money is whole kopecks, and a payment's history outlives the payment | Accepted |
| [0013](#adr-0013) | The server geocodes addresses through Nominatim, and may be told not to | Accepted |
| [0014](#adr-0014) | The map is drawn in Compose over OpenStreetMap tiles | Accepted, **amends** 0008 |
| [0015](#adr-0015) | Reminders are alarm-clock alarms, remembered for a reboot | Accepted |
| [0016](#adr-0016) | The web client is the same UI as Kotlin/Wasm, served by the server | Accepted, **amends** 0004 |

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

---

## ADR-0007

### Embedded PostgreSQL when Docker is missing

**Accepted** · 2026-09-25 · amends [ADR-0006](#adr-0006)

**Context.** ADR-0006 lets the PostgreSQL tests skip without Docker and relies on CI to run them.
On the development machine Docker is not installed, and on the day the server's schema was
written GitHub Actions had not started a single run for this repository — so the SQL would
have been merged untested everywhere.

**Decision.** `TestPostgres` tries Testcontainers first and falls back to
`io.zonky.test:embedded-postgres`, which unpacks and starts real PostgreSQL binaries (the same
major version as docker-compose, 17) from Maven. The tests skip only when neither starts; on CI
that is still a failure.

**Alternatives rejected.**
- *Keep skipping (ADR-0006 as written).* Leaves every repository and migration unverified until
  CI works and Docker is installed.
- *H2 or another in-memory database.* Not PostgreSQL: rejected in ADR-0006 for the same reasons.

**Consequences.** One more test-only dependency; the first run downloads the binaries for the
host platform. The server's database tests run on any machine the build runs on.

**Review when:** Docker is present everywhere the build runs — then the fallback can go.

---

## ADR-0008

### Requests go to a placeholder host, resolved per request

**Accepted** · 2026-09-25 · refines [ADR-0003](#adr-0003)

**Context.** ADR-0003 has API calls and image loads share one `HttpClient` whose plugin applies
the configured address and token. The domain carries file locations as server-relative paths.
Coil, though, only hands absolute `http(s)` URLs to its network fetcher, so a relative path
never reaches the client at all.

**Decision.** Every request to our server — API and images alike — is built against the
placeholder base `http://prorab-server.invalid`. The `ServerAddress` plugin in `:core:data`
recognizes that host, replaces scheme, host, port and base path with the configured server,
and adds the bearer token; requests to any other host pass untouched and never carry the
token. File locations in the domain are a `ServerFilePath` value class, so `:app` registers a
Coil mapper for exactly that type (`ServerFilePath.toRequestUrl()`).

**Alternatives rejected.**
- *Absolute URLs built in the data layer's mappers.* Go stale in memory when the address
  changes, and put the configured address into every model.
- *Resolve in a Coil interceptor or mapper against the current settings.* A second place that
  knows the server's address; the API would still need the plugin.

**Consequences.** Changing the server in the settings affects the very next request. A request
that somehow escaped the plugin goes nowhere: `.invalid` never resolves (RFC 2606). The
connection check addresses candidate settings directly, bypassing the placeholder on purpose.

**Review when:** images come from somewhere other than our server.

---

## ADR-0009

### Ask for local network access at startup

**Accepted** · 2026-09-25

**Context.** The app targets SDK 37. Android 17 puts connections to the local network behind a
runtime permission, `ACCESS_LOCAL_NETWORK` (protection level *dangerous*); without it a
connection to a LAN address simply times out. In stage 1 the server is exactly that: a computer
on the same Wi-Fi. Found on the emulator: the connection check timed out while the same address
answered from `adb shell`.

**Decision.** `:app` declares the permission and `MainActivity` requests it on start on
Android 17+, before any screen talks to the server. A refusal needs no special handling: requests
fail as `AppError.Network` and the screens say the server does not answer.

**Alternatives rejected.**
- *Ask only when a request fails.* The first failure would already be on screen, and the data
  layer would have to know about Android permissions.
- *Target an older SDK to avoid the permission.* Postpones the problem to the next Play policy
  bump and drops other platform behavior along with it.

**Consequences.** One system dialog on first launch. The request becomes unnecessary once the
server moves to a public VPS (stage "VPS"), but costs nothing until then.

**Review when:** the server no longer lives on the local network.

---

## ADR-0010

### Extra contacts are a sub-resource; the primary client stays on the object

**Accepted** · 2026-10-01

**Context.** The customer asked for several contacts per client, and for the client and the
executor to be callable from the card. The object already carries `clientName` and `clientPhone`,
which the objects list, the edit form and five test suites know about.

**Decision.** Extra people are rows of a new `contacts` table with a role (client, executor,
other), edited from the card through `/api/objects/{id}/contacts` and `/api/contacts/{id}`. The
object keeps its primary client fields, unchanged.

**Alternatives rejected.**
- *Move the client into `contacts` and drop the columns.* The honest model, but it rewrites the
  object contract, the list's client name, the form and the migration of existing data in one
  step, for no behavior the customer asked for.
- *A free-form contacts blob on the object.* Not callable, not editable one by one.

**Consequences.** A client may appear twice (on the object and as a contact with the client role).
The data is not lost when that is tidied later.

**Review when:** the primary client fields are felt to be in the way, or contacts need their own
screen.

---

## ADR-0011

### Photos and receipts are one table with a kind

**Accepted** · 2026-10-01

**Context.** Each object has two folders: photos of the work and receipts. Both are uploaded
through the same pipeline (type check, thumbnail, storage on disk, row).

**Decision.** `photos.kind` is `PHOTO` or `RECEIPT`, and the upload takes `?kind=`. A receipt never
becomes the cover and cannot be made one; deleting a cover picks the next *photo*, never a receipt.
The objects list counts photos only. The card shows two carousels; the viewer pages through the
folder of the tapped file.

**Alternatives rejected.**
- *A `receipts` table and a second upload route.* Duplicates the pipeline and its tests.

**Consequences.** A third kind is one more enum entry. Files of both kinds share the object's
directory in storage.

**Review when:** receipts need their own fields (amount, shop, date), which would make them more
than photos.

---

## ADR-0012

### Money is whole kopecks, and a payment's history outlives the payment

**Accepted** · 2026-10-01

**Context.** The books of an object: what was agreed with the client and the crew, what was paid
and when, and the extra works. The customer wants a history of payment edits.

**Decision.** Amounts are `Long` kopecks on the wire, in the database and in the domain; `Money`
parses and prints rubles, so no floating point touches an amount. The server sums what is paid and
what is left. Every create, update and delete of a payment writes a row to `payment_history` in the
same transaction; that table has no foreign key to `payments`, so deleting a payment keeps its
history (it goes with the object). The client owes the contract total plus the extras agreed on.

**Alternatives rejected.**
- *Decimal rubles.* Needs a decimal type the domain does not have, and rounding rules.
- *Soft-delete flags on payments.* Every query would have to remember the flag.

**Consequences.** The history can only grow. A payment's day is a `LocalDay` (`yyyy-MM-dd` on the
wire), with no time zone.

**Review when:** a second currency, or the history must be editable or exported.

---

## ADR-0013

### The server geocodes addresses through Nominatim, and may be told not to

**Accepted** · 2026-10-01

**Context.** The map needs a point per object, and the object has only an address.

**Decision.** `ObjectService` asks a `Geocoder` when an object is created, when its address changes,
and on `POST /api/objects/{id}/geocode`. The implementation is OpenStreetMap's Nominatim over the
JDK's HTTP client, with an identifying User-Agent and short timeouts. Anything that goes wrong —
no match, a bad answer, no network — is "not found": the object is saved with no pin, never refused.
A changed address that is not found removes the old pin. `GEOCODER_URL` points at another
Nominatim-compatible server, and `off` disables geocoding.
Addresses are written without a city, so the search carries a `viewbox` around `GEOCODER_NEAR`
(default Yekaterinburg). It is a preference, not a bound: a city typed in the address still wins.
An address may also be chosen on the map: the form opens a picker (a pin fixed in the middle, the map
moves under it), `GET /api/geocode/reverse` turns the point into an address, and the point travels with
the object request (`latitude`/`longitude`), in which case the server does not geocode. Typing another
address drops the point. The two screens do not know each other: the picker leaves its choice in a
`PickedPlaceStore` that the form's view model reads.

**Alternatives rejected.**
- *Geocode on the phone.* Every client would need a geocoder and the policy of its provider.
- *Coordinates typed in by hand.* Nobody knows the coordinates of an address.
- *Yandex or Google geocoders.* Need a key, which the customer does not have.
- *Appending the city to every query.* A different city typed in the address would be contradicted.
- *`bounded=1`.* An address in another city would silently find nothing.

**Consequences.** **The addresses of the owner's objects are sent to nominatim.openstreetmap.org**
unless `GEOCODER_URL=off`; `.env.example` and the README say so. Nominatim's one request a second
limit is not an issue at this volume. Geocoding runs inside the request that saves the object, so a
slow network adds up to its timeout to that save.

**Review when:** objects are imported in bulk, or the owner objects to addresses leaving the machine.

---

## ADR-0014

### The map is drawn in Compose over OpenStreetMap tiles

**Accepted** · 2026-10-01 · **amends** [ADR-0008](#adr-0008)

**Context.** The customer wants the objects on a map and has no key for a commercial map. A map SDK
would be Android-only, and the web client is planned.

**Decision.** `:feature:map` lays out `https://tile.openstreetmap.org` tiles itself — Web Mercator
maths in `WebMercator`, drag and pinch in `MapViewport` — and draws pins on top. Tiles load through
Coil, so through the app's `HttpClient`. That client now sends a `User-Agent` naming the app, which
OpenStreetMap's tile policy asks for. The attribution is shown wherever the map is.

ADR-0008 had said "review when images come from somewhere other than our server". This is that
review: the `ServerAddress` plugin still adds the token only to requests for the placeholder host,
so a tile request carries none, which a test pins down.

**Alternatives rejected.**
- *osmdroid or MapLibre.* Android-only, and they bring their own tile fetching and caching.
- *Yandex MapKit.* Needs a key and a licence.

**Consequences.** No offline map and no tile cache beyond Coil's. The tile server's usage policy
applies: this is an app for one business, far below its limits.

**Review when:** many users, or offline use on sites without a signal.

---

## ADR-0015

### Reminders are alarm-clock alarms, remembered for a reboot

**Accepted** · 2026-10-01

**Context.** A task may have a reminder time, and the customer called the feature "alarms". Android
decides how exact an alarm is and whether the app may set exact ones.

**Decision.** Reminders use `AlarmManager.setAlarmClock`: exact, needing no special permission, and
shown as an alarm in the status bar. A receiver posts a notification; Android 13+ gets the
notification permission request on start. `AndroidReminderScheduler.sync` makes the set of alarms
equal to the open tasks with a time ahead of now, and writes that set to `SharedPreferences`; a boot
receiver sets the remembered alarms again, with no network. `KeepRemindersUseCase` follows the tasks
for as long as the process lives, and a failed load leaves the alarms as they were.

**Alternatives rejected.**
- *`setExactAndAllowWhileIdle`.* Needs `SCHEDULE_EXACT_ALARM`, which the user must grant on Android 14.
- *WorkManager.* Not exact; it would remind late.
- *Re-fetch from the server on boot.* The phone may not be on the owner's Wi-Fi yet.

**Consequences.** Reminders are set only for tasks the phone has seen while it could reach the
server. The alarm icon shows while one is pending.

**Review when:** the server is on the internet and pushes can wake the phone instead.

---

## ADR-0016

### The web client is the same UI as Kotlin/Wasm, served by the server

**Accepted** · 2026-10-01 · **amends** [ADR-0004](#adr-0004)

**Context.** ADR-0004 kept `commonMain` free of platform APIs so a web client could reuse the
screens, and checked that the UI stack publishes `wasm-js` variants. The customer wants a web
version once the Android features are in.

**Decision.** Every client KMP module gets a `wasmJs` target from its convention plugin, and `:web`
is the browser entry point: Koin, Coil over the shared `HttpClient`, and `ComposeViewport`. The
platform parts are a handful of `wasmJsMain` files: the Ktor `Js` engine, settings in `localStorage`,
image compression through a canvas, the file chooser as the camera and gallery, and a console log.
The server serves the built site at `/` (`WEB_DIR`), so the page and the API share an origin: no
CORS, and the server address defaults to the page's own. The API token is typed into the settings
once and kept in `localStorage`; the site itself holds no secret.

The build needed three accommodations, each recorded where it is made: `base` replaces the root's
hand-made `clean` task (the Wasm toolchain wants the real one); settings repositories declare the
Node.js, Binaryen and Yarn distributions and `PREFER_SETTINGS` ignores the project repositories the
toolchain adds; and Karma, which the toolchain installs from a GitHub fork for browser tests,
is pinned to the registry's 6.4.4 because no test runs in a browser.

**Alternatives rejected.**
- *A separate JavaScript front end.* Re-implements every screen and drifts from the app.
- *The web served by a separate static host.* Needs CORS on the server and a second place to configure.
- *Baking the token into the site.* Anyone who can load the page would have it.

**Consequences.** The web client has no reminders (no `ReminderScheduler`; the day plan itself works).
`verify` builds the site, which takes a few minutes the first time. The map's tile loading and
the settings screen are shared with Android as they are. A browser needs WebAssembly GC.

**Review when:** the web client must work offline, or must be installable (a service worker and a
manifest), or the token should not live in `localStorage`.

