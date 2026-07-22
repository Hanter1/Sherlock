# Catalog

Площадки и справочники живут в assets и могут обновляться без нового APK.

## Файлы в APK

| Файл | Назначение |
|------|------------|
| `app/src/main/assets/osint_sites.json` | URL-шаблоны, маркеры, категории, `rateLimitMs` |
| `app/src/main/assets/def_codes.json` | Справочник DEF / кодов |

Категории площадок: `dev` / `social` / `gaming` / `media` / `design` / `creator`.

Каталог v6: у площадок есть `okBodyMarkers` и/или `trustHttpStatus` — меньше ложных FOUND.

## Пресеты скана

В настройках: Все / Соцсети / Dev / Медиа / РБ (+ опции Instagram/X).

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
