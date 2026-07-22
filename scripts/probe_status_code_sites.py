#!/usr/bin/env python3
import json
import ssl
import time
import urllib.error
import urllib.request
from pathlib import Path

UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
sites = {s["name"]: s for s in json.loads(Path("app/src/main/assets/osint_sites.json").read_text(encoding="utf-8"))["sites"]}
checks = [
    ("Behance", "adobe", "zzznobodyexists99999qqqxyz"),
    ("Reddit", "spez", "zzznobodyexists99999qqqxyz"),
    ("Roblox", "Roblox", "zzznobodyexists99999qqqxyz"),
    ("YouTube", "YouTube", "zzznobodyexists99999qqqxyz"),
    ("DockerHub", "library", "zzznobodyexists99999qqqxyz"),
]


def fetch(url: str):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    try:
        with urllib.request.urlopen(req, context=ssl.create_default_context(), timeout=20) as r:
            return r.status
    except urllib.error.HTTPError as e:
        return e.code
    except Exception as e:  # noqa: BLE001
        return str(e)


for name, good, bad in checks:
    s = sites[name]
    tmpl = s.get("urlProbe") or s["urlTemplate"]
    cg = fetch(tmpl.replace("{user}", good))
    time.sleep(0.2)
    cb = fetch(tmpl.replace("{user}", bad))
    ok = isinstance(cg, int) and 200 <= cg <= 299 and cb in (404, 410)
    print(("OK" if ok else "PROBLEM"), name, "good", cg, "bad", cb)
