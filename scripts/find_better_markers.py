#!/usr/bin/env python3
import re
import ssl
import urllib.error
import urllib.request

UA = (
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
)


def fetch(url: str):
    req = urllib.request.Request(
        url,
        headers={"User-Agent": UA, "Accept-Language": "ru-RU,ru;q=0.9,en;q=0.5"},
    )
    try:
        with urllib.request.urlopen(req, context=ssl.create_default_context(), timeout=25) as r:
            return r.status, r.read(900_000).decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        body = e.read(400_000).decode("utf-8", "replace") if e.fp else ""
        return e.code, body
    except Exception as e:  # noqa: BLE001
        return None, str(e)


def show(name: str, body: str, needles: list[str]) -> None:
    print("---", name, "len", len(body))
    m = re.search(r'property="og:title" content="([^"]+)"', body)
    print("og:title:", m.group(1) if m else None)
    m = re.search(r"<title>([^<]+)</title>", body)
    print("title:", m.group(1) if m else None)
    for needle in needles:
        i = body.lower().find(needle.lower())
        hit = i >= 0
        snip = body[max(0, i - 15) : i + 70].replace("\n", " ") if hit else ""
        print(f"  {'HIT' if hit else 'miss'} {needle!r} {snip[:100]}")


def main() -> None:
    c, b = fetch("https://vk.com/durov")
    print("VK HTTP", c)
    show(
        "VK good",
        b,
        [
            "og:type",
            "vk:page_id",
            "owner_id",
            "screen_name",
            "durov",
            '"id":1',
            "Profile",
            "memlink",
            "page_current",
            "data-testid",
            "wall_module",
            "Олег Дуров",
            "Pavel",
        ],
    )
    # dump interesting meta
    for m in re.finditer(r"<meta[^>]+>", b[:20000]):
        t = m.group(0)
        if any(x in t.lower() for x in ("og:", "vk:", "twitter:")):
            print("META", t[:180])

    c, b = fetch("https://old.reddit.com/user/spez")
    print("\nOLD REDDIT HTTP", c)
    show("old reddit", b, ["user-profile", "ProfileTrophyShowcase", "about-main", "thing", "spez"])

    c, b = fetch("https://www.reddit.com/user/spez/about.json")
    print("\nREDDIT JSON HTTP", c, b[:200].replace("\n", " "))

    c, b = fetch("https://www.behance.net/adobe")
    print("\nBEHANCE HTTP", c)
    show(
        "behance",
        b,
        [
            '"username":"adobe"',
            '"displayName"',
            "og:type",
            "profile",
            "__NEXT_DATA__",
            "behance.net/adobe",
        ],
    )

    c, b = fetch("https://www.behance.net/zzznobodyexists99999qqqxyz")
    print("\nBEHANCE BAD HTTP", c)
    show("behance bad", b, ['"username":"adobe"', "Oops!", "404", "profile-info", "og:title"])

    c, b = fetch("https://www.roblox.com/users/profile?username=Roblox")
    print("\nROBLOX HTTP", c)
    show("roblox", b, ["og:title", "data-userid", "profileuserid", "users/1/profile", "Roblox's Profile"])

    c, b = fetch("https://www.pinterest.com/pinterest/")
    print("\nPINTEREST HTTP", c)
    show(
        "pinterest",
        b,
        [
            '"username":"pinterest"',
            "pinterestapp:username",
            "og:title",
            "UserProfile",
            "profile-header",
        ],
    )

    c, b = fetch("https://ok.ru/durov")
    print("\nOK durov HTTP", c)
    show("ok durov", b, ["does not exist", "profile-user", "hookBlock", "og:title"])

    c, b = fetch("https://ok.ru/profile/561658513202")
    print("\nOK profile id HTTP", c)
    show("ok id", b, ["profile-user", "hookBlock", "og:title", "does not exist"])


if __name__ == "__main__":
    main()
