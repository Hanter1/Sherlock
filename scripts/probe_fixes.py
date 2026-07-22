#!/usr/bin/env python3
import ssl
import urllib.error
import urllib.request

UA = (
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
)


def fetch(url: str):
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept-Language": "en-US,en;q=0.9"})
    try:
        with urllib.request.urlopen(req, context=ssl.create_default_context(), timeout=25) as r:
            return r.status, r.read(500_000).decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, (e.read(300_000).decode("utf-8", "replace") if e.fp else "")
    except Exception as e:  # noqa: BLE001
        return None, str(e)


def check(url: str, markers: list[str]) -> None:
    code, body = fetch(url)
    print(url, "HTTP", code, "len", len(body) if body else 0)
    for m in markers:
        print(" ", ("HIT" if m.lower() in body.lower() else "miss"), repr(m))


MARK = [
    "Sorry, nobody on Reddit goes by that name",
    "page not found",
    "user-profile",
    "ProfileTrophyShowcase",
    "about-main",
    "pagename",
    "thing",
    "profile-title",
]

check("https://old.reddit.com/user/spez", MARK)
check("https://old.reddit.com/user/zzznobodyexists99999qqqxyz", MARK)
check(
    "https://www.roblox.com/user.aspx?username=Roblox",
    ["og:title", "Profile", "404", "Page Not found", "users/"],
)
check(
    "https://www.roblox.com/user.aspx?username=zzznobodyexists99999qqqxyz",
    ["og:title", "Profile", "404", "Page Not found", "users/"],
)
check(
    "https://x.com/elonmusk",
    [
        "User Profile Not Found",
        "twitter://user?screen_name=",
        'property="og:title"',
        "UserProfileHeader",
    ],
)
check(
    "https://x.com/zzznobodyexists99999qqqxyz",
    [
        "User Profile Not Found",
        "twitter://user?screen_name=",
        'property="og:title"',
        "UserProfileHeader",
    ],
)
