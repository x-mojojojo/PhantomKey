package com.phantomkey.app

import android.app.Application
import com.phantomkey.app.data.BiometricVault
import com.phantomkey.app.data.PreferencesRepository
import com.phantomkey.app.util.ClipboardHelper

class PhantomKeyApp : Application() {

    lateinit var preferences: PreferencesRepository
        private set

    lateinit var biometricVault: BiometricVault
        private set

    lateinit var clipboardHelper: ClipboardHelper
        private set

    val session = SessionState()

    override fun onCreate() {
        super.onCreate()
        preferences = PreferencesRepository(this)
        biometricVault = BiometricVault(this)
        clipboardHelper = ClipboardHelper(this)
    }
}
