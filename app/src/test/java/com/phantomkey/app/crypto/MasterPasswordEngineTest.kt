package com.phantomkey.app.crypto

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verification against the official Master Password algorithm vectors.
 *
 * Spec vectors provided by the PhantomKey brief:
 *   full name      = "Robert"
 *   master password = "password123"
 *   site           = "twitter.com"
 *   → Maximum password: S$YknOb*PVY(BfeO4&1^
 *   → Login name:       meqcoloba
 *
 * Also covers the well-known self-test from the reference implementation:
 *   user / password / example.com / long → ZedaFaxcZaso9*
 */
class MasterPasswordEngineTest {

    @Test
    fun phantomKeyVerificationVector_maximumAndLogin() {
        val key = MasterPasswordEngine.deriveMasterKey(
            fullName = "Robert",
            masterPassword = "password123",
        )

        val password = MasterPasswordEngine.generatePassword(
            masterKey = key,
            site = "twitter.com",
            counter = 1,
            type = PasswordType.MAXIMUM,
        )
        val login = MasterPasswordEngine.generateLoginName(
            masterKey = key,
            site = "twitter.com",
            counter = 1,
        )

        assertEquals("S\$YknOb*PVY(BfeO4&1^", password)
        assertEquals("meqcoloba", login)
    }

    @Test
    fun referenceSelfTest_longPassword() {
        val key = MasterPasswordEngine.deriveMasterKey(
            fullName = "user",
            masterPassword = "password",
        )
        val password = MasterPasswordEngine.generatePassword(
            masterKey = key,
            site = "example.com",
            counter = 1,
            type = PasswordType.LONG,
        )
        assertEquals("ZedaFaxcZaso9*", password)
    }

    @Test
    fun allPasswordTypesAreDeterministic() {
        val key = MasterPasswordEngine.deriveMasterKey("Robert", "password123")
        for (type in PasswordType.entries) {
            val a = MasterPasswordEngine.generatePassword(key, "twitter.com", 1, type)
            val b = MasterPasswordEngine.generatePassword(key, "twitter.com", 1, type)
            assertEquals("Type $type must be deterministic", a, b)
            assert(a.isNotEmpty()) { "Type $type produced empty password" }
        }
    }

    @Test
    fun counterRotatesPassword() {
        val key = MasterPasswordEngine.deriveMasterKey("Robert", "password123")
        val p1 = MasterPasswordEngine.generatePassword(key, "twitter.com", 1, PasswordType.LONG)
        val p2 = MasterPasswordEngine.generatePassword(key, "twitter.com", 2, PasswordType.LONG)
        assert(p1 != p2) { "Different counters must produce different passwords" }
    }

    @Test
    fun differentSitesProduceDifferentPasswords() {
        val key = MasterPasswordEngine.deriveMasterKey("Robert", "password123")
        val twitter = MasterPasswordEngine.generatePassword(key, "twitter.com", 1, PasswordType.LONG)
        val github = MasterPasswordEngine.generatePassword(key, "github.com", 1, PasswordType.LONG)
        assert(twitter != github)
    }
}
