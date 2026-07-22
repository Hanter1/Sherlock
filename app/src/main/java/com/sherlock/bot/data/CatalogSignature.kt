package com.sherlock.bot.data

import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * ECDSA P-256 signatures for remote OSINT catalogs.
 *
 * Payload (UTF-8):
 * ```
 * sherlock-catalog-v1
 * <version>
 * <updated>
 * <deterministic sites digest>
 * ```
 */
object CatalogSignature {
    const val ALGORITHM = "SHA256withECDSA"
    const val CURVE_BITS = 256

    /**
     * Project public key (X.509 SubjectPublicKeyInfo, base64).
     * Private key stays local: scripts/catalog-keys/private_pkcs8_b64.txt
     */
    const val PUBLIC_KEY_X509_B64 =
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEjLnULs8eeu4WIPWiZ63+KE1AcNNHHSKzGAoJKBrbTWL3lcn2H60IAFu1uicwFfLHuL8ngkPIKpnN3S8ZIPOmaA=="

    fun payloadBytes(version: Int, updated: String, sites: List<OsintSite>): ByteArray {
        val digest = sitesDigest(sites)
        return buildString {
            append("sherlock-catalog-v1\n")
            append(version)
            append('\n')
            append(updated)
            append('\n')
            append(digest)
        }.toByteArray(Charsets.UTF_8)
    }

    fun sitesDigest(sites: List<OsintSite>): String {
        val lines = sites.map { site ->
            listOf(
                site.name,
                site.urlTemplate,
                site.okCodes.sorted().joinToString(","),
                site.errorCodes.sorted().joinToString(","),
                site.errorBodyMarkers.joinToString("\u0001"),
                site.blockBodyMarkers.joinToString("\u0001"),
                site.okBodyMarkers.joinToString("\u0001"),
                site.categories.joinToString(","),
                site.useHead.toString(),
                site.rateLimitMs.toString(),
                site.trustHttpStatus.toString(),
                site.errorType.id,
                site.regexCheck,
                site.urlProbe,
                site.nsfw.toString(),
                site.curated.toString(),
                site.requestHeaders.toSortedMap().entries.joinToString(";") { "${it.key}=${it.value}" },
            ).joinToString("|")
        }
        return CatalogRepository.sha256Hex(lines.joinToString("\n"))
    }

    fun verify(payload: ByteArray, signatureB64: String, publicKeyB64: String = PUBLIC_KEY_X509_B64): Boolean {
        if (signatureB64.isBlank()) return false
        return runCatching {
            val key = decodePublicKey(publicKeyB64)
            val sig = Signature.getInstance(ALGORITHM)
            sig.initVerify(key)
            sig.update(payload)
            sig.verify(Base64.getDecoder().decode(signatureB64.trim()))
        }.getOrDefault(false)
    }

    fun sign(payload: ByteArray, privateKeyB64: String): String {
        val key = decodePrivateKey(privateKeyB64)
        val sig = Signature.getInstance(ALGORITHM)
        sig.initSign(key)
        sig.update(payload)
        return Base64.getEncoder().encodeToString(sig.sign())
    }

    fun decodePublicKey(b64: String): ECPublicKey {
        val spec = X509EncodedKeySpec(Base64.getDecoder().decode(b64.trim()))
        return KeyFactory.getInstance("EC").generatePublic(spec) as ECPublicKey
    }

    fun decodePrivateKey(b64: String): ECPrivateKey {
        val spec = PKCS8EncodedKeySpec(Base64.getDecoder().decode(b64.trim()))
        return KeyFactory.getInstance("EC").generatePrivate(spec) as ECPrivateKey
    }

    /** Test helper: ephemeral keypair. */
    fun generateKeyPairB64(): Pair<String, String> {
        val gen = KeyPairGenerator.getInstance("EC")
        gen.initialize(CURVE_BITS)
        val kp = gen.generateKeyPair()
        val pub = Base64.getEncoder().encodeToString(kp.public.encoded)
        val priv = Base64.getEncoder().encodeToString(kp.private.encoded)
        return pub to priv
    }
}
