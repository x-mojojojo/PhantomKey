package com.phantomkey.app.data

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Stores a non-reversible verifier for the master password so the app can
 * unlock / reject wrong passwords without ever persisting the password itself.
 *
 * Uses PBKDF2-HMAC-SHA256 (available on all API 26+ devices). This is only a
 * local unlock gate — the actual password material for generation is derived
 * separately via scrypt in [com.phantomkey.app.crypto.MasterPasswordEngine].
 */
object PasswordVerifier {

    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16

    fun createSalt(): String {
        val salt = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(salt)
        return Base64.encodeToString(salt, Base64.NO_WRAP)
    }

    fun hash(password: String, saltBase64: String): String {
        val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val hash = factory.generateSecret(spec).encoded
            Base64.encodeToString(hash, Base64.NO_WRAP)
        } finally {
            spec.clearPassword()
        }
    }

    fun verify(password: String, saltBase64: String, expectedHashBase64: String): Boolean {
        val actual = hash(password, saltBase64)
        return MessageDigest.isEqual(
            actual.toByteArray(Charsets.UTF_8),
            expectedHashBase64.toByteArray(Charsets.UTF_8),
        )
    }
}
