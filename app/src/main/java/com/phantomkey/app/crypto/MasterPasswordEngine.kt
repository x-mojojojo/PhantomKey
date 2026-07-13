package com.phantomkey.app.crypto

import org.bouncycastle.crypto.generators.SCrypt
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Pure, deterministic implementation of the Master Password algorithm (v3).
 *
 * Nothing is stored. The same (full name, master password, site, counter, type)
 * always yields the same password / login name.
 *
 * Spec summary:
 *   salt        = NS || uint32be(len(name)) || name
 *   masterKey   = scrypt(masterPassword, salt, N=32768, r=8, p=2, dkLen=64)
 *   seed        = HMAC-SHA-256(masterKey, ns || uint32be(len(site)) || site || int32be(counter))
 *   password    = applyTemplate(seed, type.templates)
 *
 * Namespaces:
 *   authentication / password  → "com.lyndir.masterpassword"
 *   identification / login     → "com.lyndir.masterpassword.login"
 */
object MasterPasswordEngine {

    const val VERSION: Int = 3

    private const val NS = "com.lyndir.masterpassword"
    private const val AUTH_NS = "com.lyndir.masterpassword"
    private const val LOGIN_NS = "com.lyndir.masterpassword.login"

    private const val SCRYPT_N = 32768
    private const val SCRYPT_R = 8
    private const val SCRYPT_P = 2
    private const val SCRYPT_DK_LEN = 64

    /** Character class map used by password templates. */
    private val PASS_CHARS: Map<Char, String> = mapOf(
        'V' to "AEIOU",
        'C' to "BCDFGHJKLMNPQRSTVWXYZ",
        'v' to "aeiou",
        'c' to "bcdfghjklmnpqrstvwxyz",
        'A' to "AEIOUBCDFGHJKLMNPQRSTVWXYZ",
        'a' to "AEIOUaeiouBCDFGHJKLMNPQRSTVWXYZbcdfghjklmnpqrstvwxyz",
        'n' to "0123456789",
        'o' to "@&%?,=[]_:-+*$#!'^~;()/.",
        // Note: the 'x' class deliberately omits some punctuation present in 'o'
        // (matches the official Master Password / Spectre character map).
        'x' to "AEIOUaeiouBCDFGHJKLMNPQRSTVWXYZbcdfghjklmnpqrstvwxyz0123456789!@#$%^&*()",
        ' ' to " ",
    )

    /**
     * Derive the 64-byte master key from full name + master password.
     * This is intentionally expensive (scrypt) so it should be run off the main thread.
     */
    fun deriveMasterKey(fullName: String, masterPassword: String): ByteArray {
        require(fullName.isNotEmpty()) { "Full name must not be empty" }
        require(masterPassword.isNotEmpty()) { "Master password must not be empty" }

        val nameBytes = fullName.toByteArray(StandardCharsets.UTF_8)
        val passwordBytes = masterPassword.toByteArray(StandardCharsets.UTF_8)
        val nsBytes = NS.toByteArray(StandardCharsets.UTF_8)

        val salt = ByteBuffer.allocate(nsBytes.size + 4 + nameBytes.size)
            .order(ByteOrder.BIG_ENDIAN)
            .put(nsBytes)
            .putInt(nameBytes.size)
            .put(nameBytes)
            .array()

        return try {
            SCrypt.generate(
                passwordBytes,
                salt,
                SCRYPT_N,
                SCRYPT_R,
                SCRYPT_P,
                SCRYPT_DK_LEN,
            )
        } finally {
            // Best-effort wipe of password material.
            passwordBytes.fill(0)
        }
    }

    /**
     * Generate a site password for the given master key.
     *
     * @param masterKey 64-byte key from [deriveMasterKey]
     * @param site site / service name (e.g. "twitter.com")
     * @param counter rotation counter (default 1)
     * @param type password template type
     */
    fun generatePassword(
        masterKey: ByteArray,
        site: String,
        counter: Int = 1,
        type: PasswordType = PasswordType.MAXIMUM,
    ): String {
        require(site.isNotEmpty()) { "Site must not be empty" }
        require(counter in 1..Int.MAX_VALUE) { "Counter out of range" }
        require(masterKey.size == SCRYPT_DK_LEN) { "Invalid master key length" }

        val seed = calculateSeed(masterKey, site, counter, AUTH_NS)
        return applyTemplate(seed, type)
    }

    /**
     * Generate a deterministic login / user name for the given site.
     * Uses the identification namespace and the "name" template.
     */
    fun generateLoginName(
        masterKey: ByteArray,
        site: String,
        counter: Int = 1,
    ): String {
        require(site.isNotEmpty()) { "Site must not be empty" }
        require(counter in 1..Int.MAX_VALUE) { "Counter out of range" }
        require(masterKey.size == SCRYPT_DK_LEN) { "Invalid master key length" }

        val seed = calculateSeed(masterKey, site, counter, LOGIN_NS)
        return applyTemplate(seed, PasswordType.NAME)
    }

    /**
     * Convenience: generate both password and login name in one call.
     */
    fun generateCredentials(
        masterKey: ByteArray,
        site: String,
        counter: Int = 1,
        type: PasswordType = PasswordType.MAXIMUM,
    ): Credentials {
        return Credentials(
            password = generatePassword(masterKey, site, counter, type),
            loginName = generateLoginName(masterKey, site, counter),
        )
    }

    data class Credentials(
        val password: String,
        val loginName: String,
    )

    // region internals

    private fun calculateSeed(
        masterKey: ByteArray,
        site: String,
        counter: Int,
        namespace: String,
    ): ByteArray {
        val siteBytes = site.toByteArray(StandardCharsets.UTF_8)
        val nsBytes = namespace.toByteArray(StandardCharsets.UTF_8)

        val data = ByteBuffer.allocate(nsBytes.size + 4 + siteBytes.size + 4)
            .order(ByteOrder.BIG_ENDIAN)
            .put(nsBytes)
            .putInt(siteBytes.size)
            .put(siteBytes)
            .putInt(counter)
            .array()

        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(masterKey, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun applyTemplate(seed: ByteArray, type: PasswordType): String {
        val templates = type.templates
        val template = templates[seed[0].toUByte().toInt() % templates.size]
        val builder = StringBuilder(template.length)
        for (i in template.indices) {
            val classChar = template[i]
            val chars = PASS_CHARS[classChar]
                ?: error("Unknown template character: '$classChar'")
            val index = seed[i + 1].toUByte().toInt() % chars.length
            builder.append(chars[index])
        }
        return builder.toString()
    }

    // endregion
}
