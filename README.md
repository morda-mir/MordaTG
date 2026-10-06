# MordaTG

MordaTG — локальный Telegram-прокси для Android 8.0 и новее. При первой установке
приложение выбирает и сохраняет случайный локальный порт из диапазона
`49152..65535`. SOCKS5 принимает TCP CONNECT от приложений,
которые пользователь настроил вручную, и передаёт поддерживаемый Telegram
MTProto-трафик через WebSocket/TLS-маршрут, совместимый с KROT.

MordaTG **не** использует `VPNService`, root, системный прокси, перехват пакетов,
телеметрию, рекламные SDK или стороннюю аналитику.

## Архитектура

- `ui` — Compose UI, экраны состояния, настройки Telegram, диагностика и обновления;
- `service` — foreground service и восстановление выбранного состояния;
- `socks` — loopback-only SOCKS5, лимиты соединений и двунаправленный bridge;
- `upstream` — распознавание Telegram DC, MTProto framing и WSS с проверкой TLS;
- `network` — событийная реакция на смену активной сети;
- `diagnostics` — локальные счётчики и проверки по запросу пользователя;
- `updates` — HTTPS manifest, загрузка по действию пользователя, SHA-256 и системный установщик;
- `morda` — безопасная JSON-конфигурация первого информационного блока;
- `storage` — небольшие локальные настройки без содержимого трафика.

Точное соответствие Windows-проекту описано в
[`docs/KROT_COMPATIBILITY.md`](docs/KROT_COMPATIBILITY.md).

## Сборка

Требования:

- JDK 17;
- Android SDK Platform 36;
- Android SDK Build Tools 36.0.0.

Укажите SDK в `local.properties` (файл не коммитится):

```properties
sdk.dir=C\:\\Android\\Sdk
```

Debug APK:

```powershell
.\gradlew.bat :app:assembleDebug
```

Результат: `app/build/outputs/apk/debug/app-debug.apk`.

Тесты:

```powershell
.\gradlew.bat testDebugUnitTest
```

Полная локальная проверка:

```powershell
.\gradlew.bat clean testDebugUnitTest lintDebug assembleDebug
```

## Release keystore

Создайте ключ один раз в защищённом каталоге **за пределами репозитория**:

```powershell
keytool -genkeypair -v `
  -keystore D:\Secure\MordaTG\mordatg-release.jks `
  -alias mordatg `
  -keyalg RSA -keysize 4096 -validity 10000
```

Скопируйте `release-signing.properties.example` в
`release-signing.properties` и укажите абсолютный путь, alias и пароли. Реальный
файл настроек и `*.jks` исключены из Git.

Подписанный release APK:

```powershell
.\gradlew.bat :app:assembleRelease
```

Результат: `app/build/outputs/apk/release/app-release.apk`.

Проверьте подпись:

```powershell
$env:ANDROID_HOME\build-tools\36.0.0\apksigner.bat verify --verbose --print-certs `
  app\build\outputs\apk\release\app-release.apk
```

### Что обязательно сохранить

1. `mordatg-release.jks` — без него Android не позволит обновить установленное приложение;
2. пароль хранилища, alias и пароль ключа — в отдельном менеджере паролей;
3. минимум две зашифрованные резервные копии ключа на независимых носителях;
4. SHA-256 сертификата (`keytool -list -v -keystore ...`) для сверки будущих релизов.

Никогда не пересоздавайте ключ между версиями и не отправляйте его в Git, чат,
почту или систему сборки без защищённого secret storage.

## Настройка Telegram

1. Запустите прокси в MordaTG.
2. Нажмите «Подключить» и подтвердите стандартную SOCKS5-ссылку.
3. Если deep link не открылся: Telegram → Настройки → Данные и память →
   Настройки прокси → Добавить прокси → SOCKS5.
4. Сервер: `127.0.0.1`, порт указан на главном экране, логин и пароль пустые.

Если сохранённый порт занят, MordaTG выбирает другой свободный порт из
диапазона `49152..65535`, сохраняет его и подставляет в копирование и Telegram
deep link. После смены порта нажмите «Обновить».

## Сетевые параметры

- loopback и порт: `app/src/main/java/online/morda/mordatg/core/ProxyConstants.kt`;
- Telegram DC и WSS: `app/src/main/java/online/morda/mordatg/upstream/`;
- совместимый fallback-пул KROT: `UpstreamConnector.kt`;
- лимит активных соединений: `ProxyConstants.MAX_CONNECTIONS`.

TLS использует системное хранилище доверия Android, SNI и HTTPS hostname
verification. Режима отключения проверки сертификатов нет.

## Обновления

Передайте Gradle property `mordatgUpdateEndpoint` как HTTPS URL. Для GitHub
Releases используйте стабильный адрес:

```text
https://github.com/OWNER/REPOSITORY/releases/latest/download/update.json
```

Например:

```powershell
.\gradlew.bat :app:assembleRelease `
  -PmordatgUpdateEndpoint=https://github.com/OWNER/REPOSITORY/releases/latest/download/update.json
```

Workflow `.github/workflows/release.yml` собирает подписанный APK, формирует
`update.json` с SHA-256 и публикует оба файла в GitHub Releases. Формат manifest:
[`docs/update-manifest.example.json`](docs/update-manifest.example.json).

Репозиторий или как минимум release-артефакты должны быть публично доступны:
приложение не хранит GitHub-токен и не сможет обновляться из приватного Releases.
Для ручного запуска workflow укажите существующий или новый тег вида `v0.1.0`.

Приложение:

- не проверяет обновления постоянным polling;
- загружает APK только после действия пользователя;
- ограничивает размер ответа;
- проверяет SHA-256 до запуска системного установщика;
- не выполняет скрытую установку;
- хранит временный APK только в cache приложения.

## morda.online

Необязательный удалённый endpoint задаётся property `mordatgMordaEndpoint`.
Без него используется локальный блок с Telegram deep link на
`@morda_online_bot`. Формат: [`docs/morda-content.example.json`](docs/morda-content.example.json).

Разрешены только текст, необязательное HTTPS-изображение, подпись кнопки и HTTPS
URL. Удалённый HTML и произвольный код не исполняются. При отсутствии endpoint
используется локальный fallback, а ссылка открывается только после нажатия.

## Следующая версия

1. Увеличьте `versionCode` и `versionName` в `app/build.gradle.kts`.
2. Запустите тесты, lint и release-сборку.
3. Проверьте подпись тем же сертификатом.
4. Посчитайте `Get-FileHash -Algorithm SHA256 app-release.apk`.
5. Опубликуйте APK по HTTPS.
6. Обновите manifest: версия, changelog, SHA-256, дата и минимальная версия.
7. Не удаляйте предыдущий APK, пока обновление не проверено на реальном устройстве.

## Разрешения Android

- `INTERNET` — WSS, manifest обновлений и morda.online;
- `ACCESS_NETWORK_STATE` — событийная реакция на потерю/смену сети;
- `FOREGROUND_SERVICE` и `FOREGROUND_SERVICE_SPECIAL_USE` — длительная работа
  явно включённого локального прокси без шестичасового лимита `dataSync`;
- `POST_NOTIFICATIONS` — видимое малозаметное уведомление на Android 13+;
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — обязательный системный запрос фоновой
  работы при первом включении прокси;
- `RECEIVE_BOOT_COMPLETED` — необязательное восстановление после перезагрузки;
- `REQUEST_INSTALL_PACKAGES` — переход к стандартному установщику скачанного APK.

Разрешений на VPN, геолокацию, хранилище, контакты, камеру и микрофон нет.

## Фон и энергосбережение

Прокси использует блокирующее ожидание сокетов на `Dispatchers.IO`, сетевой
callback и foreground service. Нет busy-loop, постоянного WakeLock, частого
таймера или фонового health polling. Health-check запускается пользователем.

Samsung, Xiaomi/Redmi/POCO, Huawei/Honor, OPPO/Realme и другие прошивки могут
завершать foreground service. При включении MordaTG требует разрешить фоновую
работу; на Android 13+ сначала требует уведомления. Без этих разрешений прокси
не запускается.

## Ограничения

- SOCKS5 намеренно принимает только известные адреса/порты Telegram, поэтому это
  не универсальный прокси.
- Внутри установленного SOCKS-соединения поддерживаются MTProto abridged,
  intermediate и padded-intermediate с transport obfuscation, которые нужны WSS.
- Переподключение выполняется на уровне маршрута до установки сессии; при обрыве
  активной MTProto-сессии соединение закрывается, после чего Telegram создаёт новое.
- Производители Android могут ограничить автозапуск, несмотря на разрешённый
  `BOOT_COMPLETED`.
- Production endpoint обновлений, production endpoint morda.online, финальная
  иконка и release keystore должны быть предоставлены владельцем проекта.

