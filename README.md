# Sherlock Bot

Мобильное Android-приложение OSINT: тёмный кабинет (режимы, запрос, отчёт, журнал), открытые источники.

## Что умеет

- **Кабинет** — тёмный console UI: вкладки режимов, поле запроса, карточка отчёта, журнал
- **Поиск по никнейму** — 40+ площадок с категориями и `rateLimitMs` (`osint_sites.json`)
- **Сравнение ников** — `/compare a b`; **Δ с прошлого скана** при «Повторить без кэша»
- **Телефон** — приоритет **Беларусь `+375`**, также `+7` / `+380` / `+1` / `+44`
- **Email** — MX + SPF/DMARC (DoH) + Gravatar; согласие + тумблеры в настройках
- **Share/copy** — предупреждение при ПДн; опция маскирования телефона/email
- **Каталог v6** — `okBodyMarkers` или `trustHttpStatus` у всех площадок (меньше ложных FOUND)
- **ФИО** — Google BY / Yandex BY / VK
- **Remote-каталог** — URL в настройках + sha256/version без нового APK
- **Поиск по истории** — лупа в шапке; закрепление последнего отчёта
- **Дисклеймер** — при первом запуске; скрытие в «Недавних» (FLAG_SECURE)
- **Отмена скана** — кнопка «Стоп» во время проверки ника
- **Сводка + фильтры** — найденные / неуверенно / нет / ошибки + группировка по категориям
- **Добить ошибки** — повтор только ERROR + UNCERTAIN площадок
- **История** — шифрование на диске (EncryptedFile); опция «не сохранять»
- **Повторить без кэша** — принудительный рескан ника
- **Экспорт** — Markdown / JSON с `confidence` (confirmed / uncertain)
- **Настройки** — параллелизм, пресет площадок (Все/Соцсети/Dev/Медиа/РБ), Instagram/X, email, история, remote-каталог
- **Remote-каталог** — HTTPS + allowlist; опционально ECDSA-подпись (`signature`); скрипт `scripts/sign_catalog.py`
- **Журнал дел** — группировка по дням, тип (ник/email/…), удаление одного дела
- **CI** — unit-тесты + `lintDebug` + debug APK; MockWebServer для cancel/retry
- **История чата** — журнал запросов на устройстве; очистка из журнала / `/clear`
- **Поделиться / Копировать** — системный шаринг или буфер обмена
- **Офлайн** — индикатор сети в шапке; ник/email требуют сеть; телефон и ФИО локально
- **Кэш ника** — сессия + диск (TTL 24 ч); в отчёте и настройках — возраст / остаток TTL
- **Очередь ников** — `/username a b c` или через запятую (до 5 подряд)
- **Retry** — повтор при 429/5xx + per-host rate limit (очередь по хосту)
- **Диагностика HTTP** — код / редиректы / причина в отчёте (UNCERTAIN, ERROR, rate limited)
- **Уведомление** — скан завершён в фоне (`POST_NOTIFICATIONS`)
- **О приложении** — версия APK + версии каталогов (кнопка ℹ / `/about`)

CI: GitHub Actions (`.github/workflows/ci.yml`) — `testDebugUnitTest` + `assembleDebug` (JDK и SDK ставятся в runner; локально не обязательны).  
Weekly catalog probe: `.github/workflows/catalog-probe.yml` (`scripts/probe_catalog.py`).

Каталоги (без переписывания Kotlin):
- площадки: `app/src/main/assets/osint_sites.json` (`categories`: dev / social / gaming / media / design / creator)
- DEF: `app/src/main/assets/def_codes.json`

Приложение **не** подключается к закрытым базам и утечкам. Это легальный OSINT-клиент по открытым источникам.

## Стек

Kotlin · Jetpack Compose · OkHttp · Coroutines

## Сборка debug APK

Нужны JDK 17 и Android SDK.

```bat
set JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot
set ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk
gradlew.bat assembleDebug
```

APK: `app\build\outputs\apk\debug\app-debug.apk`

```bat
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## Сборка release (R8)

Release включает minify + shrink resources.

1. Создайте keystore (один раз):

```bat
keytool -genkey -v -keystore release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias sherlock
```

2. Скопируйте `keystore.properties.example` → `keystore.properties` в корне репозитория и заполните пароли / путь к `.jks`.

3. Соберите:

```bat
gradlew.bat assembleRelease
```

APK: `app\build\outputs\apk\release\app-release.apk`  
(если `keystore.properties` нет — APK будет unsigned / потребуется подпись вручную).

`keystore.properties` и `*.jks` в git не коммитятся.

## Использование

1. Откройте приложение
2. Нажмите **Никнейм** / **Телефон** / **Email** / **ФИО** или введите команду `/username durov`
3. Дождитесь отчёта в чате (ссылки кликабельны; markdown: `code`, *жирный*)

## Важно

Используйте только для законных целей и с уважением к приватности. Авторы не несут ответственности за злоупотребление инструментом.
