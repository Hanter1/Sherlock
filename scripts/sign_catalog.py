#!/usr/bin/env python3
"""Sign osint_sites.json with the local ECDSA P-256 private key.

Requires: private key at scripts/catalog-keys/private_pkcs8_b64.txt
Uses the same payload as CatalogSignature (via a tiny Java helper if available),
or a pure-Python fallback matching CatalogSignature.sitesDigest / payloadBytes.

Usage:
  python scripts/sign_catalog.py app/src/main/assets/osint_sites.json
"""

from __future__ import annotations

import base64
import hashlib
import json
import sys
from pathlib import Path

try:
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import ec
    from cryptography.hazmat.primitives.asymmetric.utils import decode_dss_signature
except ImportError:
    print("Install cryptography: pip install cryptography", file=sys.stderr)
    sys.exit(1)

ROOT = Path(__file__).resolve().parents[1]
PRIV = ROOT / "scripts" / "catalog-keys" / "private_pkcs8_b64.txt"


def sites_digest(sites: list[dict]) -> str:
    lines = []
    for site in sites:
        ok = ",".join(str(x) for x in sorted(site.get("okCodes", [200])))
        err = ",".join(str(x) for x in sorted(site.get("errorCodes", [404])))
        lines.append(
            "|".join(
                [
                    site["name"],
                    site["urlTemplate"],
                    ok,
                    err,
                    "\u0001".join(site.get("errorBodyMarkers", [])),
                    "\u0001".join(site.get("blockBodyMarkers", [])),
                    "\u0001".join(site.get("okBodyMarkers", [])),
                    ",".join(site.get("categories", [])),
                    str(bool(site.get("useHead", False))).lower(),
                    str(int(site.get("rateLimitMs", 0))),
                    str(bool(site.get("trustHttpStatus", False))).lower(),
                ]
            )
        )
    return hashlib.sha256("\n".join(lines).encode("utf-8")).hexdigest()


def payload_bytes(version: int, updated: str, sites: list[dict]) -> bytes:
    digest = sites_digest(sites)
    return f"sherlock-catalog-v1\n{version}\n{updated}\n{digest}".encode("utf-8")


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__)
        return 2
    if not PRIV.exists():
        print(f"Missing private key: {PRIV}", file=sys.stderr)
        return 1
    path = Path(sys.argv[1])
    data = json.loads(path.read_text(encoding="utf-8"))
    data.pop("signature", None)
    payload = payload_bytes(int(data.get("version", 1)), str(data.get("updated", "")), data["sites"])
    priv = serialization.load_der_private_key(base64.b64decode(PRIV.read_text().strip()), password=None)
    # cryptography signs with DER; Android Signature.verify expects ASN.1 DER for ECDSA — OK
    sig = priv.sign(payload, ec.ECDSA(hashes.SHA256()))
    data["signature"] = base64.b64encode(sig).decode("ascii")
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Signed {path} ({len(data['sites'])} sites)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
