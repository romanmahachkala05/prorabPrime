# ProrabPrime

An app for a small renovation business: the owner's sites as a grid you can pick from at a glance,
and a card per site with its client and other contacts (tap to call), a link to its chat, photos
and receipts, the books (payments to the crew and from the client, with a history, and extra works),
and a materials checklist. A big camera button shoots first and asks where the picture goes. Sites
are on a map (OpenStreetMap), and there is a plan for the day with reminders. Stage 1: a Ktor server
on the owner's computer and an Android app talking to it over the local Wi-Fi.

- Architecture — [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
- Why it is built this way — [`docs/DECISIONS.md`](docs/DECISIONS.md)
- Building, testing, branches and commits — [`CONTRIBUTING.md`](CONTRIBUTING.md)

## What you need

- **JDK 17 or newer** to start Gradle. The build provisions JDK 17 for itself.
- **Android SDK** (Android Studio installs it), with `sdk.dir` in `local.properties`.
- **Docker** — optional. Without it the server runs over an embedded PostgreSQL (below).

The same screens run in a browser: the server serves the web app (Kotlin/Wasm, Compose), see
[the web app](#the-web-app) below.

## 1. Configure the server

Copy `.env.example` to `.env` and replace every `change-me`:

```bash
cp .env.example .env
```

`API_TOKEN` is what the phone sends with every request: make it a long random string. One way:

```bash
python -c "import secrets; print(secrets.token_urlsafe(24))"
```

`.env` is gitignored; it never leaves the computer.

**Map pins.** To put an object on the map the server looks its address up on OpenStreetMap's
public Nominatim, which means **the address is sent to nominatim.openstreetmap.org**. If you would
rather keep addresses on this computer, set `GEOCODER_URL=off` in `.env`: objects then have no pins.
An address can be typed or chosen on the map ("Выбрать на карте" in the object form). Addresses
without a city are resolved around Yekaterinburg; change the point with `GEOCODER_NEAR`
(`.env.example`).

## 2. Start the server

**With Docker** — PostgreSQL in a container, the server from Gradle:

```bash
docker compose up -d
```

```bash
./gradlew :server:run
```

Or both in Docker (build the jar first):

```bash
./gradlew :server:buildFatJar
```

```bash
docker compose --profile server up -d --build
```

**Without Docker** — the same server over an embedded PostgreSQL whose data lives in
`data/dev-postgres`:

```bash
./gradlew :server:runDev
```

The server is up when the log says `Responding at ...:8080`. Check it from the computer:

```bash
curl http://localhost:8080/health
```

Photos are stored under `data/uploads` (gitignored).

## 3. Find the computer's address

The phone reaches the server at the computer's address in the local network.

- **Windows:** run `ipconfig` and take the *IPv4 Address* of the Wi-Fi (or Ethernet) adapter,
  e.g. `192.168.1.10`.
- **macOS / Linux:** `ipconfig getifaddr en0` or `hostname -I`.

The server address for the app is then `http://192.168.1.10:8080`.

On Windows, the firewall asks the first time the server starts whether Java may accept
connections; allow it for **private** networks. If the phone still cannot connect, check that
port 8080 is allowed for private networks in *Windows Defender Firewall → Allow an app*.

From the Android emulator the computer is `http://10.0.2.2:8080`.

## 4. Build and install the app

Optionally put the address and token in `local.properties` so a fresh install starts with them
(they can always be changed in the app's settings):

```properties
prorab.serverUrl=http://192.168.1.10:8080
prorab.apiToken=the same value as API_TOKEN in .env
```

With the phone connected over USB (developer options and USB debugging on):

```bash
./gradlew :app:installDebug
```

Or build the APK and copy it to the phone:

```bash
./gradlew :app:assembleDebug
```

The file is `app/build/outputs/apk/debug/app-debug.apk`.

**Use the debug build.** Until the server moves to a VPS with HTTPS, it speaks plain HTTP, which
only the debug build allows.

## 5. First launch

1. On Android 17 and newer the app asks for access to **devices on the local network**:
   allow it, or it cannot reach the server (ADR-0009).
2. Open **Настройки** (the gear on the list), enter the server address and the token, tap
   **Проверить соединение** — it should answer *Соединение есть, токен принят* — then
   **Сохранить**.
3. Back on the list, add the first object with **Добавить объект**.

The phone and the computer must be on the same Wi-Fi network.

## A signed release APK

The release build is signed with a key that stays on your computer. Make it once (the JDK that comes
with Android Studio has `keytool`) and **keep the file and both passwords safe: a phone only accepts an
update signed with the same key, and a lost key cannot be replaced**:

```bash
keytool -genkeypair -v -keystore prorab-release.jks -alias prorab -keyalg RSA -keysize 4096 -validity 10000
```

Put it outside the repository or next to it (`*.jks` is gitignored), and tell Gradle where it is in
`keystore.properties` (gitignored too):

```properties
storeFile=prorab-release.jks
storePassword=...
keyAlias=prorab
keyPassword=...
```

On a build machine the same four values come from `PRORAB_STORE_FILE`, `PRORAB_STORE_PASSWORD`,
`PRORAB_KEY_ALIAS` and `PRORAB_KEY_PASSWORD`. Then:

```bash
./gradlew :app:assembleRelease
```

builds `app/build/outputs/apk/release/app-release.apk`. Without a key the same task produces an unsigned
APK, which a phone will not install. The release build still speaks plain HTTP to the server (see above),
so until the server has HTTPS, use the debug build on the phone.

## Checking a change

```bash
./gradlew verify
```

Formatting, static analysis, every unit test and the debug APK; the server's PostgreSQL tests run
in Docker, or over embedded PostgreSQL when Docker is missing (ADR-0006, ADR-0007). See
[`CONTRIBUTING.md`](CONTRIBUTING.md).

## HTTPS, to reach the server from anywhere

Plain HTTP works on the home Wi‑Fi and only in the debug build. To use the server from outside, and with
the release build, put HTTPS in front of it. With a public IP address this costs nothing:

1. **A name.** On [duckdns.org](https://www.duckdns.org) make a free name such as `prorab.duckdns.org` and
   point it at your IP. If the address changes now and then, run DuckDNS's small updater so the name follows it.
   (A public IP means the router's outside address equals the one a "what is my IP" site shows; behind a
   provider's shared address this will not work.)
2. **The server stays private.** In `.env` set `HOST=127.0.0.1` and a long random `API_TOKEN`: with the
   server reachable from the internet the token is the only lock. Restart the server.
3. **Caddy.** Download [Caddy](https://caddyserver.com/download), copy `Caddyfile.example` to `Caddyfile`, put
   your name in it, and start it next to the file:

   ```bash
   caddy run
   ```

   It gets the certificate itself and renews it. To start it with Windows, `caddy start` as a service or a
   scheduled task at logon does it; the DuckDNS updater and the server want the same.
4. **The router.** Forward ports **80** and **443** to this computer (80 is used only to get the
   certificate), and allow Caddy through the Windows firewall. **Do not** forward 8080.
5. **The app and the site.** In the settings of the app give `https://prorab.duckdns.org` and the token; the
   site is at that address. The release build (see above) works with it.

Check it from outside the home network (a phone on mobile data): the address must show the site, and
`http://<your IP>:8080` must not answer. The computer has to be on, and the server, Caddy and the updater
running, for the app to reach it; without a signal the app still works from its own copy.

## Working without a signal

The app keeps its own copy of everything it shows, so the list of objects, the cards, the finance, the checklist
and the day plan open with no network, and photos, receipts and notes can be made there too. What was made
without a signal waits in a queue (a clock marks it) and goes to the server when a network is back — at once if
the app is open, and by the system's job scheduler if it has been closed. A small line at the bottom says when
there is no signal or something is waiting. If the server refuses a change for good, the line says so and opens a
list where it can be retried or given up on. Pictures seen once are kept for the next time there is none.

The browser version is online-first: its copy lives only while the tab is open.

## The trash

Deleting an object, or a photo or receipt, moves it to the trash instead of removing it. Open it from
**Настройки → Корзина**: every item shows how many days it has left (thirty in all), and can be restored
or removed for good; "Очистить" empties the trash. After thirty days the server removes what is left. The
trash is read from the server, so it needs a signal; deleting itself still works without one (ADR-0019).

## The web app

The web client is the same Compose UI compiled to WebAssembly. It needs a recent browser (Chrome,
Edge or Firefox from the last two years, Safari 18.2+). The server serves it, so the page and the API
share an address and nothing needs CORS.

```bash
./gradlew :web:wasmJsBrowserDistribution
```

builds the site into `web/build/dist/wasmJs/productionExecutable` (the first build downloads Node.js,
Yarn and Binaryen into `~/.gradle`). `.env.example` points `WEB_DIR` at that folder; start the server
and open `http://<this computer>:8080/` in a browser. The first time, open the settings (the gear),
type the API token, and press save: it is kept in that browser's `localStorage`. The server address
is the one the page came from.

In the browser the camera button opens the file chooser (a phone's browser goes straight to the
camera), and the day plan has no alarms: reminders are an Android feature.

