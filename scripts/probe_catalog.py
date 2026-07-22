#!/usr/bin/env python3
"""Weekly health probe for critical OSINT catalog entries.

Exits non-zero if GitHub / Bitbucket / Codeberg markers look broken.
"""
from __future__ import annotations

import json
import ssl
import sys
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "app" / "src" / "main" / "assets" / "osint_sites.json"

UA = (
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
)

# Stable public accounts + nonsense missing nick
PROBES = [
    {
        "name": "GitHub",
        "ok_user": "torvalds",
        "missing_user": "sherlock_probe_no_such_user_zzz9x7",
        "ok_codes": {200},
        "missing_codes": {404},
    },
    {
        "name": "Bitbucket",
        "ok_user": "atlassian",
        "missing_user": "sherlock_probe_no_such_user_zzz9x7",
        "ok_codes": {200},
        "missing_codes": {404},
    },
    {
        "name": "Codeberg",
        "ok_user": "Codeberg",
        "missing_user": "sherlock_probe_no_such_user_zzz9x7",
        "ok_codes": {200},
        "missing_codes": {404},
    },
]


def load_sites() -> dict[str, dict]:
    data = json.loads(CATALOG.read_text(encoding="utf-8"))
    return {s["name"]: s for s in data["sites"]}


def fetch(url: str, method: str = "HEAD") -> int:
    req = urllib.request.Request(
        url,
        method=method,
        headers={
            "User-Agent": UA,
            "Accept": "text/html,application/xhtml+xml",
            "Accept-Language": "en-US,en;q=0.9",
        },
    )
    ctx = ssl.create_default_context()
    try:
        with urllib.request.urlopen(req, context=ctx, timeout=20) as resp:
            return int(resp.status)
    except urllib.error.HTTPError as e:
        return int(e.code)


def probe_one(site: dict, user: str, expect: set[int], label: str) -> list[str]:
    errors: list[str] = []
    url = site["urlTemplate"].replace("{user}", user)
    method = "HEAD" if site.get("useHead") else "GET"
    try:
        code = fetch(url, method=method)
        if method == "HEAD" and code in {400, 403, 405}:
            code = fetch(url, method="GET")
        if code not in expect:
            errors.append(f"{site['name']} {label} ({user}): HTTP {code}, expected {sorted(expect)} · {url}")
        else:
            print(f"OK  {site['name']:12} {label:8} HTTP {code}  {user}")
    except Exception as e:  # noqa: BLE001
        errors.append(f"{site['name']} {label} ({user}): {e} · {url}")
    return errors


def main() -> int:
    if not CATALOG.exists():
        print(f"Catalog missing: {CATALOG}", file=sys.stderr)
        return 2
    sites = load_sites()
    failures: list[str] = []
    for probe in PROBES:
        site = sites.get(probe["name"])
        if site is None:
            failures.append(f"Site missing from catalog: {probe['name']}")
            continue
        failures.extend(probe_one(site, probe["ok_user"], probe["ok_codes"], "exists"))
        failures.extend(probe_one(site, probe["missing_user"], probe["missing_codes"], "missing"))

    if failures:
        print("\nPROBE FAILED:", file=sys.stderr)
        for line in failures:
            print(f"  - {line}", file=sys.stderr)
        return 1
    print("\nAll catalog probes passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
