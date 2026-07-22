# Catalog signing keys (ECDSA P-256)
#
# public_x509_b64.txt — embedded in CatalogSignature.PUBLIC_KEY_X509_B64
# private_pkcs8_b64.txt — gitignored; keep offline
#
# Sign a catalog JSON:
#   javac -cp <path-to-org-json-and-app-classes> ...
# Or use scripts/sign_catalog.py when private key is present.

To rotate keys: regenerate with GenCatalogKeys, update PUBLIC_KEY_X509_B64, re-sign remotes.
