package com.verifyblind.sdk.crypto

import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

internal object CryptoUtils {

    /**
     * SHA-256 hash of a string, hex encoded.
     */
    fun sha256(input: String): String {
        val bytes = sha256Bytes(input)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * SHA-256 hash of a string, raw bytes.
     */
    fun sha256Bytes(input: String): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray(StandardCharsets.UTF_8))
    }

    /**
     * SHA-256 hash of raw bytes.
     */
    fun sha256Bytes(input: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input)
    }

    /**
     * Ephemeral RSA-OAEP-2048 keypair üretir (PoP Mode için).
     */
    fun generateRsaKeyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        return generator.generateKeyPair()
    }

    /**
     * RSA public key'i Base64 SPKI formatına çevirir.
     * (Web Crypto API "spki" export ile aynı format)
     */
    fun exportPublicKeyBase64(keyPair: KeyPair): String {
        // publicKey.encoded returns DER-encoded SPKI (same as Web Crypto spki export)
        return Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)
    }

    /**
     * pk_hash = SHA-256(publicKeyBase64) hex string.
     * Web Crypto ile aynı: digest("SHA-256", new TextEncoder().encode(pubKeyBase64))
     */
    fun computePkHash(publicKeyBase64: String): String {
        return sha256(publicKeyBase64)
    }

    /**
     * Enclave'den gelen hybrid şifreli yanıtı çözer.
     *
     * Format: { enc_key: Base64(RSA-OAEP(AES-key-base64)), blob: Base64(IV(12)+Ciphertext+Tag) }
     *
     * @return Çözülmüş JSON string (payload + signature içerir)
     */
    fun decryptHybridResponse(encKey: String, blob: String, keyPair: KeyPair): String {
        // 1. RSA-OAEP ile AES key'i çöz (şifrelenmiş şey: base64 kodlu AES key)
        val encKeyBytes = Base64.decode(encKey, Base64.DEFAULT)
        val oaepSpec = OAEPParameterSpec(
            "SHA-256", "MGF1",
            MGF1ParameterSpec.SHA256,
            PSource.PSpecified.DEFAULT
        )
        val rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        rsaCipher.init(Cipher.DECRYPT_MODE, keyPair.private, oaepSpec)
        val aesKeyB64Bytes = rsaCipher.doFinal(encKeyBytes)

        // Enclave AES key'i base64 olarak şifreliyor — önce base64 decode et
        val aesKeyB64 = String(aesKeyB64Bytes, StandardCharsets.UTF_8)
        val aesKeyRaw = Base64.decode(aesKeyB64, Base64.DEFAULT)

        // 2. AES-GCM decrypt — format: IV(12 bytes) + Ciphertext + Tag(16 bytes, WebCrypto: tag sonda)
        val blobBytes = Base64.decode(blob, Base64.DEFAULT)
        val iv = blobBytes.sliceArray(0 until 12)
        val cipherAndTag = blobBytes.sliceArray(12 until blobBytes.size)

        val aesKey: SecretKey = SecretKeySpec(aesKeyRaw, "AES")
        val gcmSpec = GCMParameterSpec(128, iv) // 128-bit authentication tag
        val aesCipher = Cipher.getInstance("AES/GCM/NoPadding")
        aesCipher.init(Cipher.DECRYPT_MODE, aesKey, gcmSpec)
        val plaintext = aesCipher.doFinal(cipherAndTag)

        return String(plaintext, StandardCharsets.UTF_8)
    }
}
