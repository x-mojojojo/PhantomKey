package com.phantomkey.app

import com.phantomkey.app.crypto.MasterPasswordEngine
import com.phantomkey.app.crypto.PasswordType
import java.util.Arrays

/**
 * In-memory session holding the derived master key.
 * Cleared on lock / timeout. Never persisted.
 */
class SessionState {
    @Volatile
    private var masterKey: ByteArray? = null

    @Volatile
    var fullName: String = ""
        private set

    val isUnlocked: Boolean
        get() = masterKey != null

    fun unlock(fullName: String, masterPassword: String) {
        lock()
        this.fullName = fullName
        this.masterKey = MasterPasswordEngine.deriveMasterKey(fullName, masterPassword)
    }

    fun unlockWithKey(fullName: String, key: ByteArray) {
        lock()
        this.fullName = fullName
        this.masterKey = key.copyOf()
    }

    fun lock() {
        masterKey?.let { Arrays.fill(it, 0) }
        masterKey = null
        fullName = ""
    }

    fun withKey(block: (ByteArray) -> Unit) {
        val key = masterKey ?: error("Session is locked")
        block(key)
    }

    fun generate(
        site: String,
        counter: Int,
        type: PasswordType,
    ): MasterPasswordEngine.Credentials {
        val key = masterKey ?: error("Session is locked")
        return MasterPasswordEngine.generateCredentials(key, site, counter, type)
    }
}
