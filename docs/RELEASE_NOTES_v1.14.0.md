# Sherlock Bot v1.14.0

Android OSINT-кабинет по открытым источникам.

## Download

- **Sherlock-Bot-1.14.0-debug.apk** — debug-сборка (API 26+). Подписана debug-keystore.

## What's new in 1.14

- Per-host rate limit (очередь по хосту; ошибка `rate limited`)
- HTTP-диагностика в отчёте (код, редиректы, причина UNCERTAIN/ERROR)
- Уведомление о конце скана в фоне
- Unit-тесты WorkbenchViewModel (cancel / clear / reportId)

## Docs

- [Documentation](https://github.com/Hanter1/Sherlock/blob/main/docs/wiki/Home.md)
- [README](https://github.com/Hanter1/Sherlock#readme)

## Note

Это **debug** APK. Для production соберите signed release со своим keystore ([Building](https://github.com/Hanter1/Sherlock/wiki/Building)).
