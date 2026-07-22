#!/usr/bin/env python3
"""Patch curated catalog entries after live marker audit."""

from __future__ import annotations

import json
from pathlib import Path

CATALOG = Path(__file__).resolve().parents[1] / "app/src/main/assets/osint_sites.json"


def patch(site: dict) -> None:
    name = site["name"]

    if name == "TikTok":
        # "Couldn't find this account" is an i18n string on ALL pages (Telegram-class bug).
        # __UNIVERSAL_DATA_FOR_REHYDRATION__ is also on missing pages.
        site["errorBodyMarkers"] = [
            'statusCode":10221',
            'statusCode":10222',
            "page-not-available",
        ]
        site["okBodyMarkers"] = ['"userInfo"', "followerCount", '"uniqueId"']
        site["errorType"] = "legacy"
        site["errorCodes"] = []

    elif name == "X":
        # UserProfileHeader appears in GraphQL query text on 404 pages too.
        site["errorBodyMarkers"] = [
            "User Profile Not Found",
            "This account doesn't exist",
            "Try searching for another",
        ]
        site["okBodyMarkers"] = ["twitter://user?screen_name="]
        site["errorCodes"] = [404]
        site["errorType"] = "legacy"

    elif name == "Behance":
        # profile-info is CSS on 404 shells; HTTP status is reliable (sherlock status_code).
        site["errorType"] = "status_code"
        site["errorCodes"] = [404, 410]
        site["okCodes"] = [200]
        site["errorBodyMarkers"] = []
        site["okBodyMarkers"] = []
        site["useHead"] = False
        site["trustHttpStatus"] = True

    elif name == "Roblox":
        site["urlTemplate"] = "https://www.roblox.com/user.aspx?username={user}"
        site["errorType"] = "status_code"
        site["errorCodes"] = [404, 410]
        site["okCodes"] = [200]
        site["errorBodyMarkers"] = []
        site["okBodyMarkers"] = []
        site["trustHttpStatus"] = True

    elif name == "Reddit":
        # www.reddit returns antibot shells; old.reddit still exposes 200/404.
        site["urlTemplate"] = "https://www.reddit.com/user/{user}"
        site["urlProbe"] = "https://old.reddit.com/user/{user}"
        site["errorType"] = "status_code"
        site["errorCodes"] = [404, 410]
        site["okCodes"] = [200]
        site["errorBodyMarkers"] = []
        site["okBodyMarkers"] = []
        site["trustHttpStatus"] = True
        site["requestHeaders"] = {"accept-language": "en-US,en;q=0.9"}

    elif name == "YouTube":
        site["errorType"] = "status_code"
        site["errorCodes"] = [404, 410]
        site["okCodes"] = [200]
        site["errorBodyMarkers"] = []
        site["okBodyMarkers"] = []
        site["trustHttpStatus"] = True

    elif name == "Pinterest":
        # Unauth SPA often identical for existing/missing; require strict markers.
        site["errorType"] = "legacy"
        site["errorCodes"] = [404, 410]
        site["errorBodyMarkers"] = ["Sorry! We couldn't find that page"]
        site["okBodyMarkers"] = ["pinterestapp:username", '"username":"']
        site["blockBodyMarkers"] = []

    elif name == "DockerHub":
        site["errorType"] = "status_code"
        site["urlTemplate"] = "https://hub.docker.com/u/{user}/"
        site["errorCodes"] = [404, 410]
        site["okCodes"] = [200]
        site["errorBodyMarkers"] = []
        site["okBodyMarkers"] = []
        site["trustHttpStatus"] = True

    elif name == "VK":
        # Mobile/desktop often serve captcha/login challenge without profile markers.
        site["blockBodyMarkers"] = ["captcha", "challenge"]
        site["errorBodyMarkers"] = [
            "page_not_found",
            "service_msg_null",
            "page_not_found_placeholder",
        ]
        site["okBodyMarkers"] = ["owner_id", "wall_module", "screen_name", 'property="og:title"']
        site["errorCodes"] = [404]
        site["errorType"] = "legacy"

    elif name == "OK.ru":
        site["errorBodyMarkers"] = [
            "This page does not exist on OK",
            "page-not-found",
            "страница не найдена",
        ]
        site["okBodyMarkers"] = ["profile-user", "hook_Block_UserProfile"]
        site["blockBodyMarkers"] = []
        site["errorCodes"] = [404]
        site["errorType"] = "legacy"

    elif name == "Telegram":
        # Keep ok-first engine fix; tighten missing marker (username_link is missing-only).
        site["errorBodyMarkers"] = ["tgme_icon_user", "tgme_username_link"]
        site["okBodyMarkers"] = ["tgme_page_title", "tgme_page_photo"]

    elif name == "Bluesky":
        site["errorType"] = "legacy"
        site["errorCodes"] = [400, 404]
        site["errorBodyMarkers"] = ["Profile not found"]
        site["okBodyMarkers"] = ["bsky.app/profile", 'property="og:url"']

    elif name in {"Medium", "Linktree", "LeetCode", "Codeforces", "Instagram"}:
        # Antibot / 403 shells — keep existing markers but ensure block signals.
        blocks = list(site.get("blockBodyMarkers") or [])
        for b in ("cf-challenge", "cf-browser-verification", "Just a moment", "Access Denied"):
            if b not in blocks:
                blocks.append(b)
        site["blockBodyMarkers"] = blocks


def main() -> None:
    data = json.loads(CATALOG.read_text(encoding="utf-8"))
    by_name = {s["name"]: s for s in data["sites"]}
    for name in [
        "TikTok",
        "X",
        "Behance",
        "Roblox",
        "Reddit",
        "YouTube",
        "Pinterest",
        "DockerHub",
        "VK",
        "OK.ru",
        "Telegram",
        "Bluesky",
        "Medium",
        "Linktree",
        "LeetCode",
        "Codeforces",
        "Instagram",
    ]:
        if name not in by_name:
            raise SystemExit(f"missing site {name}")
        patch(by_name[name])
        print("patched", name)

    # Bump catalog version note if present
    if "version" in data and isinstance(data["version"], int):
        data["version"] = data["version"] + 1
        print("catalog version", data["version"])

    CATALOG.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("wrote", CATALOG)


if __name__ == "__main__":
    main()
