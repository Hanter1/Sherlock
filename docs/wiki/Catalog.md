# Catalog

Площадки и справочники живут в assets и могут обновляться без нового APK.

Username-скан использует **конвертированный** список [sherlock-project/sherlock](https://github.com/sherlock-project/sherlock) (MIT) + наши curated-площадки (Telegram, BY-фокус). Движок — нативный Kotlin, не Python CLI.

Обновление из upstream:

```bash
python scripts/import_sherlock_catalog.py
```

## Файлы в APK

| Файл | Назначение |
|------|------------|
| `app/src/main/assets/osint_sites.json` | URL-шаблоны, маркеры, `errorType`, категории, `rateLimitMs` |
| `app/src/main/assets/def_codes.json` | Справочник DEF / кодов |

Категории: `dev` / `social` / `gaming` / `media` / `design` / `creator` / `other` / `nsfw`.

Пресеты скана: **Быстрый** (curated) · **Sherlock Full** (~480) · Соцсети / Dev / Медиа / РБ. NSFW — тумблер в настройках (выкл. по умолчанию).

Детект (`errorType`): `status_code` · `message` · `response_url` · `legacy` (наши маркеры).

## Remote-каталог

В настройках можно указать HTTPS URL каталога:

- хост должен быть в **allowlist**
- проверка `sha256` / `version`
- опционально поле `signature` (ECDSA)

Подпись локально:

```bash
python scripts/sign_catalog.py
```

Публичный ключ — в коде (`CatalogSignature`) и `scripts/catalog-keys/public_x509_b64.txt`.  
Приватный ключ **не** коммитится.

## CI probe

Недельный прогон: `.github/workflows/catalog-probe.yml` → `scripts/probe_catalog.py`.

## Rate limit

Глобальный параллелизм + **очередь per-host**. Ответ 429 в отчёте: ошибка `rate limited` с HTTP-диагностикой.
