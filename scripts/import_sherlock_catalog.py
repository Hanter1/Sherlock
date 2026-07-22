#!/usr/bin/env python3
"""Import sherlock-project data.json into Sherlock Bot osint_sites.json.

Upstream (MIT): https://github.com/sherlock-project/sherlock
"""
from __future__ import annotations

import json
import urllib.request
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "app" / "src" / "main" / "assets" / "osint_sites.json"
UPSTREAM = (
    "https://raw.githubusercontent.com/sherlock-project/sherlock/"
    "master/sherlock_project/resources/data.json"
)


def to_user_template(url: str) -> str:
    return url.replace("{}", "{user}")


def as_markers(error_msg) -> list[str]:
    if error_msg is None:
        return []
    if isinstance(error_msg, list):
        return [str(x) for x in error_msg if str(x).strip()]
    text = str(error_msg).strip()
    return [text] if text else []


def guess_categories(name: str, nsfw: bool) -> list[str]:
    if nsfw:
        return ["nsfw"]
    lower = name.lower()
    if any(k in lower for k in ("git", "gitlab", "bitbucket", "codeberg", "sourceforge", "hack")):
        return ["dev"]
    if any(k in lower for k in ("twitch", "youtube", "vimeo", "dribbble", "behance", "deviant")):
        return ["media"]
    if any(k in lower for k in ("steam", "xbox", "playstation", "roblox", "chess", "osu")):
        return ["gaming"]
    if any(
        k in lower
        for k in (
            "twitter",
            "facebook",
            "instagram",
            "tiktok",
            "reddit",
            "vk",
            "telegram",
            "mastodon",
            "linkedin",
            "pinterest",
        )
    ):
        return ["social"]
    return ["other"]


def convert_site(name: str, raw: dict) -> dict | None:
    url = raw.get("url")
    if not isinstance(url, str) or "{}" not in url:
        return None
    if not url.startswith("https://"):
        return None
    error_type = raw.get("errorType") or "status_code"
    if error_type not in ("status_code", "message", "response_url"):
        return None

    nsfw = bool(raw.get("isNSFW"))
    site: dict = {
        "name": name[:80],
        "urlTemplate": to_user_template(url),
        "errorType": error_type,
        "categories": guess_categories(name, nsfw),
        "nsfw": nsfw,
        "curated": False,
        "useHead": error_type == "status_code" and not raw.get("urlProbe"),
    }

    if error_type == "status_code":
        site["trustHttpStatus"] = True
        site["errorCodes"] = [404, 410]
        site["okCodes"] = [200]
    elif error_type == "message":
        markers = as_markers(raw.get("errorMsg"))
        if not markers:
            return None
        site["errorBodyMarkers"] = markers
        site["errorCodes"] = []
        site["trustHttpStatus"] = False
        site["useHead"] = False
    else:  # response_url
        site["errorCodes"] = []
        site["trustHttpStatus"] = False
        site["useHead"] = False

    regex = raw.get("regexCheck")
    if isinstance(regex, str) and regex.strip():
        site["regexCheck"] = regex.strip()[:200]

    probe = raw.get("urlProbe")
    if isinstance(probe, str) and "{}" in probe and probe.startswith("https://"):
        site["urlProbe"] = to_user_template(probe)
        site["useHead"] = False

    headers = raw.get("headers")
    if isinstance(headers, dict) and headers:
        clean = {
            str(k): str(v)[:200]
            for k, v in list(headers.items())[:16]
            if str(k).strip() and str(v).strip()
        }
        if clean:
            site["headers"] = clean

    return site


def load_curated_overrides(path: Path) -> dict[str, dict]:
    if not path.exists():
        return {}
    data = json.loads(path.read_text(encoding="utf-8"))
    out: dict[str, dict] = {}
    for site in data.get("sites", []):
        name = site.get("name")
        if not name:
            continue
        # Prefer existing curated/regional definitions over raw upstream.
        site = dict(site)
        site["curated"] = True
        if "errorType" not in site:
            if site.get("okBodyMarkers") or site.get("errorBodyMarkers"):
                site["errorType"] = "legacy"
            elif site.get("trustHttpStatus"):
                site["errorType"] = "status_code"
            else:
                site["errorType"] = "legacy"
        out[name] = site
    return out


def main() -> None:
    print(f"Fetching {UPSTREAM}")
    with urllib.request.urlopen(UPSTREAM, timeout=120) as resp:
        upstream = json.load(resp)

    converted: list[dict] = []
    skipped = 0
    for name, raw in upstream.items():
        if name.startswith("$") or not isinstance(raw, dict):
            continue
        site = convert_site(name, raw)
        if site is None:
            skipped += 1
            continue
        converted.append(site)

    overrides = load_curated_overrides(OUT)
    by_name = {s["name"]: s for s in converted}
    # Our curated sites win on conflict; also keep curated-only names not in upstream.
    for name, site in overrides.items():
        by_name[name] = site

    sites = sorted(by_name.values(), key=lambda s: s["name"].lower())
    catalog = {
        "version": 7,
        "updated": date.today().isoformat(),
        "source": "sherlock-project",
        "upstream": UPSTREAM,
        "attribution": "Site list derived from sherlock-project/sherlock (MIT).",
        "sites": sites,
    }
    OUT.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    nsfw = sum(1 for s in sites if s.get("nsfw"))
    curated = sum(1 for s in sites if s.get("curated"))
    print(
        f"Wrote {OUT} · sites={len(sites)} curated={curated} nsfw={nsfw} skipped={skipped}"
    )


if __name__ == "__main__":
    main()
