#!/usr/bin/env python3
import re
import ssl
import urllib.error
import urllib.request

UA = (
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
)


def fetch(url: str, extra=None):
    h = {"User-Agent": UA, "Accept-Language": "ru-RU,ru;q=0.9,en;q=0.5", "Accept": "text/html"}
    if extra:
        h.update(extra)
    req = urllib.request.Request(url, headers=h)
    try:
        with urllib.request.urlopen(req, context=ssl.create_default_context(), timeout=25) as r:
            return r.status, r.read(900_000), r.geturl()
    except urllib.error.HTTPError as e:
        body = e.read(400_000) if e.fp else b""
        return e.code, body, url
    except Exception as e:  # noqa: BLE001
        return None, str(e).encode(), url


def main() -> None:
    for url in [
        "https://vk.com/durov",
        "https://m.vk.com/durov",
        "https://vk.com/id1",
    ]:
        code, raw, final = fetch(url)
        # try decode
        for enc in ("utf-8", "windows-1251", "cp1251"):
            try:
                b = raw.decode(enc) if isinstance(raw, bytes) else raw
                used = enc
                break
            except Exception:
                b = ""
                used = "?"
        print("=" * 60)
        print(url, "->", final, "HTTP", code, "enc", used, "len", len(b))
        title = re.search(r"<title>([^<]+)</title>", b)
        print("title:", title.group(1) if title else None)
        for needle in [
            "login",
            "Вход",
            "owner_id",
            "screen_name",
            "wall_module",
            "page_not_found",
            "durov",
            "og:title",
            "vkontakte",
            "Profile",
            "service_msg",
            "captcha",
            "challenge",
            "id1",
            "Павел",
            "Durov",
        ]:
            print(f"  {needle!r}: {needle.lower() in b.lower() or needle in b}")
        # first 500 chars of body text-ish
        print("HEAD", re.sub(r"\s+", " ", b[:500])[:400])


if __name__ == "__main__":
    main()
