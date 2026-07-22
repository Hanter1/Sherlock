<p align="center">
  <img src="docs/assets/banner.png" alt="Sherlock Bot" width="100%" />
</p>

<p align="center">
  <strong>Android OSINT-кабинет</strong> — ник · телефон · email · ФИО<br/>
  только открытые источники · на устройстве · без закрытых баз
</p>

<p align="center">
  <a href="https://github.com/Hanter1/Sherlock/actions/workflows/ci.yml"><img src="https://github.com/Hanter1/Sherlock/actions/workflows/ci.yml/badge.svg" alt="CI" /></a>
  <a href="https://github.com/Hanter1/Sherlock/releases"><img src="https://img.shields.io/github/v/release/Hanter1/Sherlock?include_prereleases&label=release" alt="Release" /></a>
  <img src="https://img.shields.io/badge/Android-API%2026%2B-0A1220?logo=android" alt="Android API 26+" />
  <img src="https://img.shields.io/badge/Kotlin-Compose-E11D30" alt="Kotlin Compose" />
</p>

<p align="center">
  <a href="https://github.com/Hanter1/Sherlock/releases/latest">Скачать APK</a>
  ·
  <a href="https://github.com/Hanter1/Sherlock/wiki">Wiki</a>
  ·
  <a href="#сборка">Сборка</a>
</p>

---

## Зачем

Sherlock Bot — тёмный console-UI для быстрых проверок по публичным страницам: ник на 40+ площадках, разбор телефона (BY/RU/UA/…), email (MX/SPF/DMARC/Gravatar), поисковые запросы по ФИО. Всё локально на телефоне; каталог площадок обновляется без переписывания Kotlin.

<p align="center">
  <img src="docs/assets/mock-workbench.png" alt="Workbench" width="280" />
  &nbsp;
  <img src="docs/assets/mock-report.png" alt="Report" width="280" />
</p>

## Возможности

| | |
|---|---|
| **Никнейм** | Параллельный скан, пресеты, фильтры FOUND / UNCERTAIN / ERROR, «добить ошибки», Δ с прошлого скана |
| **Телефон / Email / ФИО** | Беларусь `+375` в приоритете; MX + политика DNS; Google/Yandex/VK |
| **Кабинет** | Журнал дел, поиск по истории, закрепление отчёта, экспорт MD/JSON |
| **Надёжность** | Per-host rate limit, retry 429/5xx, HTTP-диагностика, уведомление о конце скана |
| **Каталог** | `osint_sites.json` + remote HTTPS (allowlist, sha256, опционально ECDSA) |

Полный список — в [Wiki → Usage](https://github.com/Hanter1/Sherlock/wiki/Usage).

## Быстрый старт

1. Установите APK из [Releases](https://github.com/Hanter1/Sherlock/releases/latest) (debug-сборка помечена в notes).
2. Примите дисклеймер.
3. Режим **Никнейм** → `durov` или команда `/username durov`.
4. Стоп — отмена mid-scan; в фоне придёт уведомление о готовности.

Примеры команд: `/compare a b` · `/username a b c` (очередь до 5) · `/clear` · `/about`.

## Стек

Kotlin · Jetpack Compose · OkHttp · Coroutines · EncryptedFile

## Сборка

Нужны JDK 17 и Android SDK.

```bat
set JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot
set ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk
gradlew.bat assembleDebug
```

APK: `app\build\outputs\apk\debug\app-debug.apk`

Release (R8 + minify): скопируйте `keystore.properties.example` → `keystore.properties`, укажите `.jks`, затем `gradlew.bat assembleRelease`.

Подробнее: [Wiki → Building](https://github.com/Hanter1/Sherlock/wiki/Building).

CI: [`.github/workflows/ci.yml`](.github/workflows/ci.yml) — unit-тесты, `lintDebug`, debug APK.  
Каталог (недельный probe): [`scripts/probe_catalog.py`](scripts/probe_catalog.py).

## Документация

- [Installation](https://github.com/Hanter1/Sherlock/wiki/Installation)
- [Usage](https://github.com/Hanter1/Sherlock/wiki/Usage)
- [Catalog](https://github.com/Hanter1/Sherlock/wiki/Catalog)
- [Privacy & Ethics](https://github.com/Hanter1/Sherlock/wiki/Privacy-and-Ethics)
- [Building](https://github.com/Hanter1/Sherlock/wiki/Building)

Зеркало Wiki в репозитории: [`docs/wiki/`](docs/wiki/).

## Важно

Только законные цели и уважение к приватности. Приложение **не** подключается к закрытым базам и утечкам. Авторы не несут ответственности за злоупотребление.
