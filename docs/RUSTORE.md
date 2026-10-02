# Publishing to RuStore

What to build, what to type into the RuStore console, and what the moderators need. Details of the
console change; where this page is unsure it says "check in the console".

## 1. The build

The store build must not carry the owner's server address and token (README, "A signed release APK").
The address of the server may go in (the token must not); the token is typed in the settings:

```bash
./gradlew :app:assembleRelease -Pprorab.serverUrl=https://prorab.example.ru -Pprorab.apiToken=
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
> Для работы нужен сервер и токен: адрес и токен вводятся в настройках. Токен выдаёт владелец сервера
> (разработчик или тот, кто запустил свой сервер). Без рекламы и сторонних сборщиков данных.

**Screenshots:** the list of objects, an object card, the map, the day plan, the finance of an object.
Take them from the reviewer's account (section 3), never from real customers. Check the size rules in the console.

## 3. Moderators need an account

The app is empty without a server and a token, so give the moderators both. They get an account of their own
on your server (ADR-0021): they see none of your customers and you see none of theirs in the app. This needs
the version of the server with accounts running first.

1. **Update the server.** Back the database up first (the migrations `V13` and `V14` change the tables; your
   objects and tasks become the first account's, `owner`, and nothing is lost): `pg_dump`, or a copy of
   `data/dev-postgres` with the server stopped. Then rebuild and restart it. Your own `API_TOKEN` keeps working.
2. **Make the reviewer's account** (README, "Accounts for other people"):

   ```bash
   ./gradlew :server:admin --args="user add RuStore" -q
   ```

   It prints the token once. Open the app with it, and make two or three invented objects with photos, a
   receipt and a few tasks, so there is something to look at; take the screenshots in this account too.
3. **The server must be reachable over HTTPS from anywhere** (README, "HTTPS"): the moderators are not on your
   network, and the release build refuses plain HTTP. The computer, the server and Caddy must be on while the
   review lasts.
4. **After the review** take the account away with `user revoke RuStore`; its data stays, nobody can reach it.

**Note for the moderator** (fill in the address and token):

> Приложение работает с собственным сервером. Для проверки: Настройки (шестерёнка) → адрес сервера
> `https://…`, токен `…` → «Проверить соединение» → «Сохранить». Затем на списке — «Добавить объект».
> Фото делаются через приложение камеры, чек — кнопкой справа внизу.

Anybody who installs the app from the store needs an account from you in the same way: there is no sign-up in
the app, and the description says so (section 2).

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
- [ ] `prorab.version` raised; release built with no token in it; installed and tried on a device.
- [ ] Server with accounts running, over HTTPS; database backed up before the update.
- [ ] Reviewer's account made, with invented data.
- [ ] Privacy page open at its address.
- [ ] Icon, descriptions, screenshots, age rating, category entered.
- [ ] Moderator note with the server address and the reviewer's token.
- [ ] `mapping.txt` of the uploaded build kept.
