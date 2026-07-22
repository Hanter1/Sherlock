# Sherlock Bot v1.15.3

## Download

- **Sherlock-Bot-1.15.3-debug.apk** — debug-сборка (API 26+).

## What's new

Аудит детекта username-скана (как баг Telegram):

- Движок: **ok-маркеры профиля важнее** error-футера (общий класс ложных «нет»)
- **TikTok** — убран i18n-маркер «Couldn't find this account» с каждой страницы
- **X** — убран ложный `UserProfileHeader` на 404
- **Reddit / Behance / Roblox / YouTube / DockerHub** — надёжный `status_code` (для Reddit — probe через old.reddit)
- **VK / IG / Medium / …** — antibot-оболочки → Error, а не ложный Missing/Found

## Docs

- [Wiki](https://github.com/Hanter1/Sherlock/wiki)
- [README](https://github.com/Hanter1/Sherlock#readme)
