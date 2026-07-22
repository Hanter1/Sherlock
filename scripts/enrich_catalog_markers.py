import json
from pathlib import Path

path = Path("app/src/main/assets/osint_sites.json")
data = json.loads(path.read_text(encoding="utf-8"))

# Sites where HEAD/status codes are historically reliable (404 = missing).
TRUST_STATUS = {
    "GitHub", "Bitbucket", "Codeberg", "Habr", "About.me", "Keybase",
    "SoundCloud", "Flickr", "HackerNoon", "ProductHunt", "npm", "PyPI",
    "Lichess", "Dev.to", "Hashnode", "Dribbble", "Replit", "Kaggle",
    "HuggingFace", "BuyMeACoffee",
}

# Extra body markers for GET sites that currently lack okBodyMarkers.
OK_MARKERS = {
    "GitLab": ["data-username", "js-user-profile", "user-calendar"],
    "Reddit": ["ProfileTrophyShowcase", "user-profile", '"kind": "t2"'],
    "DeviantArt": ["user-link", "user-homepage"],
    "Pinterest": ["profile-header", "UserProfileHeader"],
    "Roblox": ["profile-header", "data-profileuserid"],
    "Medium": ["FollowAuthor", "data-testid"],
    "Linktree": ["linktree-logo", "ProfileCard"],
    "DockerHub": ["dockerHubHeader", "orgName"],
    "Behance": ["profile-info", "ProfileInfo"],
    "OK.ru": ["profile-user", "hookBlock"],
}

# Force GET when we add ok markers.
FORCE_GET = set(OK_MARKERS.keys())

for site in data["sites"]:
    name = site["name"]
    if name in TRUST_STATUS and not site.get("okBodyMarkers"):
        site["trustHttpStatus"] = True
    if name in OK_MARKERS:
        site["okBodyMarkers"] = OK_MARKERS[name]
        site["useHead"] = False
        site.pop("trustHttpStatus", None)

data["version"] = 6
data["updated"] = "2026-07"

path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

missing = [s["name"] for s in data["sites"] if not s.get("okBodyMarkers") and not s.get("trustHttpStatus")]
print("version", data["version"], "sites", len(data["sites"]))
print("still_uncertain_risk", missing)
print("trusted", sum(1 for s in data["sites"] if s.get("trustHttpStatus")))
print("with_ok_markers", sum(1 for s in data["sites"] if s.get("okBodyMarkers")))
