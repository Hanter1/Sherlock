#!/usr/bin/env python3
"""Probe curated catalog sites for false Missing/Found from body markers."""

from __future__ import annotations

import json
import ssl
import time
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "app/src/main/assets/osint_sites.json"

# Public famous accounts used only to validate HTML markers.
GOOD = {
    "Behance": "adobe",
    "Bluesky": "jay.bsky.social",
    "Chess.com": "hikaru",
    "Codeforces": "tourist",
    "DeviantArt": "kirokaze",
    "DockerHub": "library",
    "GitLab": "gitlab",
    "Instagram": "instagram",
    "LeetCode": "leetcode",
    "Linktree": "linktree",
    "Medium": "medium",
    "OK.ru": "durov",
    "Patreon": "patreon",
    "Pinterest": "pinterest",
    "Reddit": "spez",
    "Roblox": "Roblox",
    "Steam": "gabelogannewell",
    "Telegram": "durov",
    "TikTok": "tiktok",
    "VK": "durov",
    "X": "elonmusk",
    "YouTube": "YouTube",
    "GitHub": "torvalds",
    "Habr": "boomburum",
    "Twitch": "ninja",
    "Keybase": "chris",
}

BAD = "zzznobodyexists99999qqqxyz"
UA = (
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
)


def has_any(text: str, markers: list[str]) -> list[str]:
    low = text.lower()
    return [m for m in markers if m.lower() in low]


def fetch(url: str) -> tuple[int | None, str, str]:
    req = urllib.request.Request(
        url,
        headers={"User-Agent": UA, "Accept-Language": "en-US,en;q=0.9"},
    )
    try:
        with urllib.request.urlopen(req, context=ssl.create_default_context(), timeout=20) as r:
            body = r.read(500_000).decode("utf-8", "replace")
            return r.status, body, r.geturl()
    except urllib.error.HTTPError as e:
        body = e.read(500_000).decode("utf-8", "replace") if e.fp else ""
        return e.code, body, url
    except Exception as e:  # noqa: BLE001
        return None, str(e), url


def classify(text: str, err: list[str], ok: list[str], prefer_ok: bool, code: int | None, error_codes: list[int]) -> str:
    if code is not None and error_codes and code in error_codes:
        return "Missing"
    if prefer_ok:
        if ok and has_any(text, ok):
            return "Found"
        if err and has_any(text, err):
            return "Missing"
        if ok:
            return "Missing"
        return "?"
    if err and has_any(text, err):
        return "Missing"
    if ok:
        return "Found" if has_any(text, ok) else "Missing"
    return "?"


def main() -> None:
    data = json.loads(CATALOG.read_text(encoding="utf-8"))
    focus = [
        s
        for s in data["sites"]
        if s.get("curated") and (s.get("errorBodyMarkers") or s.get("okBodyMarkers"))
    ]
    print(f"curated with body markers: {len(focus)}")
    problems: list[tuple[str, list[str], str]] = []

    for s in focus:
        name = s["name"]
        tmpl = s.get("urlTemplate") or ""
        if "{user}" not in tmpl:
            continue
        good = GOOD.get(name)
        if not good:
            print(f"SKIP {name}: no known good username")
            continue

        bad_user = "zzznobody.bsky.social" if name == "Bluesky" else BAD
        probe_tmpl = s.get("urlProbe") or tmpl
        url_g = probe_tmpl.replace("{user}", good)
        url_b = probe_tmpl.replace("{user}", bad_user)
        err = s.get("errorBodyMarkers") or []
        ok = s.get("okBodyMarkers") or []
        error_codes = list(s.get("errorCodes") or [])
        et = s.get("errorType") or "legacy"

        cg, bg, _ = fetch(url_g)
        time.sleep(0.25)
        cb, bb, _ = fetch(url_b)

        if cg is None:
            print(f"SKIP {name}: good fetch fail {bg[:100]}")
            continue

        err_on_good = has_any(bg, err)
        ok_on_good = has_any(bg, ok)
        err_on_bad = has_any(bb, err) if cb is not None else []
        ok_on_bad = has_any(bb, ok) if cb is not None else []

        def engine_class(code: int | None, text: str, prefer_ok: bool) -> str:
            if code is None:
                return "ERR"
            if et == "status_code":
                if 200 <= code <= 299:
                    return "Found"
                if code in (404, 410) or code in error_codes:
                    return "Missing"
                return "Error"
            return classify(text, err, ok, prefer_ok, code, error_codes)

        og = engine_class(cg, bg, prefer_ok=False)
        ng = engine_class(cg, bg, prefer_ok=True)
        ob = engine_class(cb, bb, prefer_ok=False)
        nb = engine_class(cb, bb, prefer_ok=True)

        antibot = name in {
            "Instagram",
            "X",
            "VK",
            "OK.ru",
            "Pinterest",
            "Medium",
            "Linktree",
            "LeetCode",
            "Codeforces",
        }

        flags: list[str] = []
        if ng != "Found":
            if antibot and ng == "Missing" and et == "legacy":
                flags.append(f"GOOD_ANTIBOT_SHELL(new={ng})")
            else:
                flags.append(
                    f"GOOD_NOT_FOUND(new={ng},old={og},errOnGood={err_on_good},okOnGood={ok_on_good})"
                )
        if nb == "Found":
            flags.append(f"BAD_FALSE_FOUND(okOnBad={ok_on_bad})")
        if err_on_good and ok_on_good:
            flags.append("OVERLAP_BOTH_MARKERS_ON_GOOD")
        if ok and not ok_on_good and not antibot:
            flags.append("OK_MARKERS_STALE_ON_GOOD")
        if err and cb is not None and not err_on_bad and nb not in {"Found", "Missing"}:
            flags.append(f"ERR_MARKERS_MISS_ON_BAD(nb={nb})")

        line = (
            f"{name}: type={et} goodHTTP={cg} badHTTP={cb} "
            f"oldG={og} newG={ng} oldB={ob} newB={nb}"
        )
        if flags:
            print("PROBLEM", line, "|", "; ".join(flags))
            problems.append((name, flags, line))
        else:
            print("OK", line)

    print("---")
    print(f"problems: {len(problems)}")
    for name, flags, _ in problems:
        print(name, "->", "; ".join(flags))


if __name__ == "__main__":
    main()
