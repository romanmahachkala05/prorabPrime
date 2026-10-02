# Publishing to RuStore

What to build, what to type into the RuStore console, and what the moderators need. Details of the
console change; where this page is unsure it says "check in the console".

## 1. The build

The store build must not carry the owner's server address and token (README, "A signed release APK").
Give it the demo server instead (section 3), or none:

```bash
./gradlew :app:assembleRelease -Pprorab.serverUrl=https://demo.example.ru -Pprorab.apiToken=<demo token>
```

- Output: `app/build/outputs/apk/release/app-release.apk`, signed with the key from `keystore.properties`.
  RuStore takes an APK; `./gradlew :app:bundleRelease` makes an AAB if the console asks for one.
- The release build is minified by R8. A crash report from it needs
  `app/build/outputs/mapping/release/mapping.txt` of the same build: keep it with the APK.
- **Every upload needs a higher version.** Raise `prorab.version` in `gradle.properties`; `versionCode` follows it.
- Check the APK on a device before uploading: install it, open the list, make an object, add a photo.

## 2. The listing

| Field | Value |
| --- | --- |
| Name | Прораб Прайм |
| Package | `ru.prorabprime` |
| Category | Business / Productivity (check the list in the console) |
| Icon | [`docs/rustore/icon-512.png`](rustore/icon-512.png) |
| Privacy policy | `https://<your server>/privacy.html` |
| Age rating | 0+: no user content shared between people, no ads, no purchases |
| Price | Free, no purchases, no ads |

**Short description** (80 characters):

> Журнал бригадира: объекты, фото, чеки, расходы и план дня

**Full description:**

> Прораб Прайм — рабочий журнал для бригадира и мастера.
>
> • Объекты: адрес, статус, заметки, обложка и карта со всеми объектами.
> • Фото и чеки: снимайте прямо с экрана списка, сразу к нужному объекту.
> • Расходы: суммы по объектам, свои и по чекам.
> • Задачи и план дня с напоминаниями, месяц целиком.
> • Работает без связи: всё, что сделано без сети, отправится на сервер, когда она появится.
> • Корзина на 30 дней: случайно удалённое можно вернуть.
>
> Приложению нужен свой сервер: адрес и токен вводятся в настройках. Данные хранятся у вас,
> без рекламы и сторонних сборщиков данных.

**Screenshots:** the list of objects, an object card, the map, the day plan, the finance of an object.
Take them from the demo server (section 3), never from real customers. Check the size rules in the console.

## 3. Moderators need a server

The app is empty without one. Give the moderators a demo server and put its address and token in the
"note for the moderator" field. **Do not give them the working server**: today one token sees everything
(ADR-0005), so a second token on it would show them real customers.

A second, separate instance, on its own database, files and token:

- On Docker: a second compose project, with its own `.env` (`PORT=8090`, `DB_URL` for its own database,
  `STORAGE_DIR` its own folder, a new `API_TOKEN`, `GEOCODER_URL=off`).
- Without Docker, `./gradlew :server:runDev` starts an embedded PostgreSQL, but on a fixed port (5433) and in
  `data/dev-postgres`: it cannot run next to another `runDev`. Run the demo from a separate checkout.
- HTTPS in front of it, as in the README: the release build refuses plain HTTP. A second name
  (`prorab-demo.duckdns.org`) and a second block in the `Caddyfile` for port 8090.
- Fill it with two or three invented objects with photos, a receipt and some tasks.

**Note for the moderator** (fill in the address and token):

> Приложение работает с собственным сервером. Для проверки: Настройки (шестерёнка) → адрес сервера
> `https://…`, токен `…` → «Проверить соединение» → «Сохранить». Затем на списке — «Добавить объект».
> Фото делаются через приложение камеры, чек — кнопкой справа внизу.

## 4. Permissions to explain

| Permission | Why |
| --- | --- |
| `INTERNET`, `ACCESS_NETWORK_STATE` | the server, and knowing when a network is back |
| `ACCESS_LOCAL_NETWORK` | Android 17+: a server on the home network |
| `POST_NOTIFICATIONS` | reminders of the day's tasks |
| `RECEIVE_BOOT_COMPLETED` | set the reminders again after a reboot |

There is no camera or storage permission: photos come from the camera app and the system photo picker.

## 5. Checklist

- [ ] Developer account in the RuStore console, verified.
- [ ] `prorab.version` raised; release built with the demo address; installed and tried on a device.
- [ ] Demo server up, over HTTPS, with its own token and invented data.
- [ ] Privacy page open at its address.
- [ ] Icon, descriptions, screenshots, age rating, category entered.
- [ ] Moderator note with the demo address and token.
- [ ] `mapping.txt` of the uploaded build kept.
