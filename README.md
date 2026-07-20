# Sherlock Bot

Мобильное Android-приложение с UX как у OSINT Telegram-бота: чат, кнопки-меню, отчёты по запросу.

## Что умеет

- **Поиск по никнейму** — проверка публичных профилей на ~20 площадках (GitHub, GitLab, Reddit, Telegram, VK, TikTok и др.)
- **Телефон** — нормализация `+7` и эвристика оператора по DEF-коду
- **Email** — разбор адреса
- **ФИО** — ссылки на публичный поиск (Google / Yandex / VK)

Приложение **не** подключается к закрытым базам и утечкам. Это легальный OSINT-клиент по открытым источникам.

## Стек

Kotlin · Jetpack Compose · OkHttp · Coroutines

## Сборка APK

Нужны JDK 17 и Android SDK.

```bat
set JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot
set ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk
gradlew.bat assembleDebug
```

APK появится здесь:

`app\build\outputs\apk\debug\app-debug.apk`

Установка на устройство:

```bat
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## Использование

1. Откройте приложение
2. Нажмите **Никнейм** / **Телефон** / **Email** / **ФИО** или введите команду `/username durov`
3. Дождитесь отчёта в чате (ссылки кликабельны)

## Важно

Используйте только для законных целей и с уважением к приватности. Авторы не несут ответственности за злоупотребление инструментом.
