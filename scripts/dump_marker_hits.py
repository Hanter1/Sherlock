#!/usr/bin/env python3
"""Dump which markers hit on good/bad pages for selected sites."""

from __future__ import annotations

import json
import ssl
import time
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "app/src/main/assets/osint_sites.json"
UA = (
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
)

TARGETS = [
    ("VK", "durov", "zzznobodyexists99999qqqxyz"),
    ("TikTok", "tiktok", "zzznobodyexists99999qqqxyz"),
    ("Reddit", "spez", "zzznobodyexists99999qqqxyz"),
    ("OK.ru", "durov", "zzznobodyexists99999qqqxyz"),
    ("Behance", "adobe", "zzznobodyexists99999qqqxyz"),
    ("X", "elonmusk", "zzznobodyexists99999qqqxyz"),
    ("Roblox", "Roblox", "zzznobodyexists99999qqqxyz"),
    ("Pinterest", "pinterest", "zzznobodyexists99999qqqxyz"),
    ("YouTube", "YouTube", "zzznobodyexists99999qqqxyz"),
    ("Steam", "gabelogannewell", "zzznobodyexists99999qqqxyz"),
    ("Bluesky", "jay.bsky.social", "zzznobody.bsky.social"),
    ("GitHub", "torvalds", "zzznobodyexists99999qqqxyz"),
]


def fetch(url: str) -> tuple[int | None, str]:
    req = urllib.request.Request(
        url,
        headers={
            "User-Agent": UA,
            "Accept-Language": "en-US,en;q=0.9",
            "Accept": "text/html,application/xhtml+xml",
        },
    )
    try:
        with urllib.request.urlopen(req, context=ssl.create_default_context(), timeout=25) as r:
            return r.status, r.read(700_000).decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        body = e.read(700_000).decode("utf-8", "replace") if e.fp else ""
        return e.code, body
    except Exception as e:  # noqa: BLE001
        return None, str(e)


def snippets(body: str, needle: str, n: int = 80) -> str:
    i = body.lower().find(needle.lower())
    if i < 0:
        return ""
    a = max(0, i - 40)
    b = min(len(body), i + len(needle) + 40)
    return body[a:b].replace("\n", " ")


def main() -> None:
    sites = {s["name"]: s for s in json.loads(CATALOG.read_text(encoding="utf-8"))["sites"]}
    for name, good, bad in TARGETS:
        s = sites.get(name)
        if not s:
            print(f"MISSING SITE {name}")
            continue
        tmpl = s["urlTemplate"]
        err = s.get("errorBodyMarkers") or []
        ok = s.get("okBodyMarkers") or []
        print("=" * 72)
        print(name, "err=", err, "ok=", ok)
        for label, user in (("GOOD", good), ("BAD", bad)):
            url = tmpl.replace("{user}", user)
            code, body = fetch(url)
            print(f"  {label} @{user} HTTP={code} len={len(body)}")
            if code is None:
                print("   ", body[:120])
                continue
            for m in err:
                hit = m.lower() in body.lower()
                print(f"   ERR {'HIT' if hit else 'miss'}: {m!r} {snippets(body,m)[:100]}")
            for m in ok:
                hit = m.lower() in body.lower()
                print(f"   OK  {'HIT' if hit else 'miss'}: {m!r} {snippets(body,m)[:100]}")
            # helpful generic signals
            for g in (
                "og:title",
                "og:description",
                "twitter:title",
                "page_not_found",
                "Not Found",
                "doesn't exist",
                "does not exist",
                "profile",
                "screen_name",
                "owner_id",
                "uniqueId",
                "userInfo",
            ):
                if g.lower() in body.lower():
                    print(f"   SIG HIT: {g!r} {snippets(body,g)[:90]}")
            time.sleep(0.2)


if __name__ == "__main__":
    main()
