package com.phantomkey.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.phantomkey.app.PhantomKeyApp
import com.phantomkey.app.crypto.PasswordType
import com.phantomkey.app.data.PasswordVerifier
import com.phantomkey.app.data.SiteHistoryEntry
import com.phantomkey.app.util.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class GeneratedOutput(
    val site: String = "",
    val password: String = "",
    val loginName: String = "",
    val counter: Int = 1,
    val type: PasswordType = PasswordType.MAXIMUM,
)

data class AppUiState(
    val ready: Boolean = false,
    val setupComplete: Boolean = false,
    val unlocked: Boolean = false,
    val fullName: String = "",
    val biometricsEnabled: Boolean = false,
    val biometricsAvailable: Boolean = false,
    val sessionTimeoutMinutes: Int = 5,
    val clipboardClearSeconds: Int = 30,
    val siteInput: String = "",
    val counter: Int = 1,
    val passwordType: PasswordType = PasswordType.MAXIMUM,
    val output: GeneratedOutput = GeneratedOutput(),
    val generating: Boolean = false,
    val history: List<SiteHistoryEntry> = emptyList(),
    val busy: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val passwordVisible: Boolean = false,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PhantomKeyApp
    private val prefs = app.preferences
    private val session = app.session
    private val clipboard = app.clipboardHelper
    private val biometricVault = app.biometricVault

    private val sessionManager = SessionManager(viewModelScope)

    private val _ui = MutableStateFlow(AppUiState())
    val ui: StateFlow<AppUiState> = _ui.asStateFlow()

    private var generateJob: Job? = null

    init {
        viewModelScope.launch {
            prefs.setupComplete.collect { complete ->
                _ui.update { it.copy(setupComplete = complete, ready = true) }
            }
        }
        viewModelScope.launch {
            prefs.fullName.collect { name ->
                _ui.update { it.copy(fullName = name) }
            }
        }
        viewModelScope.launch {
            prefs.biometricsEnabled.collect { enabled ->
                _ui.update {
                    it.copy(
                        biometricsEnabled = enabled && biometricVault.isEnrolled(),
                    )
                }
            }
        }
        viewModelScope.launch {
            prefs.sessionTimeoutMinutes.collect { minutes ->
                sessionManager.setTimeoutMinutes(minutes)
                _ui.update { it.copy(sessionTimeoutMinutes = minutes) }
            }
        }
        viewModelScope.launch {
            prefs.clipboardClearSeconds.collect { seconds ->
                _ui.update { it.copy(clipboardClearSeconds = seconds) }
            }
        }
        viewModelScope.launch {
            prefs.defaultPasswordType.collect { typeName ->
                val type = PasswordType.fromName(typeName)
                _ui.update {
                    if (it.siteInput.isBlank()) it.copy(passwordType = type) else it
                }
            }
        }
        viewModelScope.launch {
            prefs.siteHistory.collect { history ->
                _ui.update { it.copy(history = history) }
            }
        }
        viewModelScope.launch {
            sessionManager.locked.collect { locked ->
                if (locked && session.isUnlocked) {
                    performLock()
                }
                _ui.update { it.copy(unlocked = session.isUnlocked && !locked) }
            }
        }
    }

    fun clearMessages() {
        _ui.update { it.copy(errorMessage = null, infoMessage = null) }
    }

    fun touchSession() {
        sessionManager.touch()
    }

    // region Setup / Unlock

    fun completeSetup(fullName: String, password: String, confirm: String) {
        if (fullName.isBlank()) {
            _ui.update { it.copy(errorMessage = "Please enter your full name.") }
            return
        }
        if (password.length < 8) {
            _ui.update { it.copy(errorMessage = "Master password must be at least 8 characters.") }
            return
        }
        if (password != confirm) {
            _ui.update { it.copy(errorMessage = "Passwords do not match.") }
            return
        }

        viewModelScope.launch {
            _ui.update { it.copy(busy = true, errorMessage = null) }
            try {
                val salt = PasswordVerifier.createSalt()
                val hash = withContext(Dispatchers.Default) {
                    PasswordVerifier.hash(password, salt)
                }
                prefs.completeSetup(fullName.trim(), hash, salt)
                // Derive key & unlock
                withContext(Dispatchers.Default) {
                    session.unlock(fullName.trim(), password)
                }
                sessionManager.unlock()
                _ui.update {
                    it.copy(
                        busy = false,
                        setupComplete = true,
                        unlocked = true,
                        fullName = fullName.trim(),
                        infoMessage = "Vault ready. Your master key never leaves this device.",
                    )
                }
            } catch (e: Exception) {
                _ui.update {
                    it.copy(busy = false, errorMessage = e.message ?: "Setup failed")
                }
            }
        }
    }

    fun unlockWithPassword(password: String) {
        if (password.isEmpty()) {
            _ui.update { it.copy(errorMessage = "Enter your master password.") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, errorMessage = null) }
            try {
                val salt = prefs.getMasterPasswordSalt()
                val expected = prefs.getMasterPasswordHash()
                if (salt == null || expected == null) {
                    _ui.update {
                        it.copy(busy = false, errorMessage = "No master password configured.")
                    }
                    return@launch
                }
                val ok = withContext(Dispatchers.Default) {
                    PasswordVerifier.verify(password, salt, expected)
                }
                if (!ok) {
                    _ui.update {
                        it.copy(busy = false, errorMessage = "Incorrect master password.")
                    }
                    return@launch
                }
                val name = prefs.getFullName()
                withContext(Dispatchers.Default) {
                    session.unlock(name, password)
                }
                sessionManager.unlock()
                _ui.update {
                    it.copy(busy = false, unlocked = true, fullName = name, errorMessage = null)
                }
            } catch (e: Exception) {
                _ui.update {
                    it.copy(busy = false, errorMessage = e.message ?: "Unlock failed")
                }
            }
        }
    }

    /**
     * Called after a successful biometric CryptoObject authentication.
     * Decrypts the stored master password and unlocks the session.
     */
    fun unlockWithBiometricCipher(cipher: javax.crypto.Cipher) {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, errorMessage = null) }
            try {
                val password = biometricVault.decrypt(cipher)
                val name = prefs.getFullName()
                withContext(Dispatchers.Default) {
                    session.unlock(name, password)
                }
                sessionManager.unlock()
                _ui.update {
                    it.copy(busy = false, unlocked = true, fullName = name)
                }
            } catch (e: Exception) {
                _ui.update {
                    it.copy(
                        busy = false,
                        errorMessage = "Biometric unlock failed. Use your master password.",
                    )
                }
            }
        }
    }

    /**
     * Enroll biometrics: encrypt current master password under biometric key.
     * Caller must supply an authenticated encrypt Cipher from BiometricPrompt.
     */
    fun enrollBiometrics(cipher: javax.crypto.Cipher, masterPassword: String) {
        viewModelScope.launch {
            try {
                biometricVault.storeEncrypted(cipher, masterPassword)
                prefs.setBiometricsEnabled(true)
                _ui.update {
                    it.copy(
                        biometricsEnabled = true,
                        infoMessage = "Biometric unlock enabled.",
                    )
                }
            } catch (e: Exception) {
                _ui.update {
                    it.copy(errorMessage = "Could not enable biometrics: ${e.message}")
                }
            }
        }
    }

    fun disableBiometrics() {
        viewModelScope.launch {
            biometricVault.clear()
            prefs.setBiometricsEnabled(false)
            _ui.update {
                it.copy(biometricsEnabled = false, infoMessage = "Biometric unlock disabled.")
            }
        }
    }

    fun lockNow() {
        performLock()
        sessionManager.lock()
    }

    private fun performLock() {
        session.lock()
        generateJob?.cancel()
        _ui.update {
            it.copy(
                unlocked = false,
                siteInput = "",
                counter = 1,
                output = GeneratedOutput(),
                passwordVisible = false,
            )
        }
    }

    // endregion

    // region Generator

    fun onSiteChanged(site: String) {
        touchSession()
        // If selecting from history, restore counter/type
        val match = _ui.value.history.firstOrNull { it.site.equals(site, ignoreCase = true) }
        _ui.update {
            it.copy(
                siteInput = site,
                counter = match?.counter ?: it.counter.coerceAtLeast(1),
                passwordType = match?.type() ?: it.passwordType,
            )
        }
        scheduleGenerate()
    }

    fun onCounterChanged(counter: Int) {
        touchSession()
        _ui.update { it.copy(counter = counter.coerceIn(1, Int.MAX_VALUE)) }
        scheduleGenerate()
    }

    fun incrementCounter() = onCounterChanged(_ui.value.counter + 1)
    fun decrementCounter() = onCounterChanged((_ui.value.counter - 1).coerceAtLeast(1))

    fun onPasswordTypeChanged(type: PasswordType) {
        touchSession()
        _ui.update { it.copy(passwordType = type) }
        scheduleGenerate()
        viewModelScope.launch { prefs.setDefaultPasswordType(type.name) }
    }

    fun togglePasswordVisibility() {
        touchSession()
        _ui.update { it.copy(passwordVisible = !it.passwordVisible) }
    }

    fun selectHistoryEntry(entry: SiteHistoryEntry) {
        touchSession()
        _ui.update {
            it.copy(
                siteInput = entry.site,
                counter = entry.counter,
                passwordType = entry.type(),
            )
        }
        scheduleGenerate()
    }

    private fun scheduleGenerate() {
        generateJob?.cancel()
        val site = _ui.value.siteInput.trim()
        if (site.isEmpty() || !session.isUnlocked) {
            _ui.update { it.copy(output = GeneratedOutput(), generating = false) }
            return
        }
        // Instant feel: small debounce so rapid typing doesn't thrash scrypt-free HMAC path
        generateJob = viewModelScope.launch {
            _ui.update { it.copy(generating = true) }
            delay(40)
            val state = _ui.value
            val siteNow = state.siteInput.trim()
            if (siteNow.isEmpty() || !session.isUnlocked) {
                _ui.update { it.copy(generating = false, output = GeneratedOutput()) }
                return@launch
            }
            try {
                val credentials = withContext(Dispatchers.Default) {
                    session.generate(siteNow, state.counter, state.passwordType)
                }
                _ui.update {
                    it.copy(
                        generating = false,
                        output = GeneratedOutput(
                            site = siteNow,
                            password = credentials.password,
                            loginName = credentials.loginName,
                            counter = state.counter,
                            type = state.passwordType,
                        ),
                    )
                }
                // History is saved only when the user copies a credential —
                // never while typing intermediate site names.
            } catch (e: Exception) {
                _ui.update {
                    it.copy(
                        generating = false,
                        errorMessage = e.message ?: "Generation failed",
                    )
                }
            }
        }
    }

    fun copyPassword() {
        touchSession()
        val password = _ui.value.output.password
        if (password.isEmpty()) return
        clipboard.copySensitive(
            label = "PhantomKey password",
            text = password,
            clearAfterSeconds = _ui.value.clipboardClearSeconds,
        )
        rememberCurrentSiteInHistory()
        _ui.update { it.copy(infoMessage = "Password copied — clipboard will clear soon.") }
    }

    fun copyLogin() {
        touchSession()
        val login = _ui.value.output.loginName
        if (login.isEmpty()) return
        clipboard.copySensitive(
            label = "PhantomKey login",
            text = login,
            clearAfterSeconds = _ui.value.clipboardClearSeconds,
        )
        rememberCurrentSiteInHistory()
        _ui.update { it.copy(infoMessage = "Login name copied.") }
    }

    /**
     * Persist site metadata only after the user actually uses a credential
     * (copy password / login). Never stores the generated secret itself.
     */
    private fun rememberCurrentSiteInHistory() {
        val output = _ui.value.output
        val site = output.site.ifBlank { _ui.value.siteInput.trim() }
        if (site.isBlank()) return
        viewModelScope.launch {
            prefs.upsertHistory(
                SiteHistoryEntry(
                    site = site,
                    counter = output.counter.coerceAtLeast(_ui.value.counter),
                    passwordType = (output.type.takeIf { output.password.isNotEmpty() }
                        ?: _ui.value.passwordType).name,
                ),
            )
        }
    }

    // endregion

    // region Settings / History

    fun setSessionTimeout(minutes: Int) {
        viewModelScope.launch {
            prefs.setSessionTimeoutMinutes(minutes)
            sessionManager.setTimeoutMinutes(minutes)
        }
    }

    fun setClipboardClearSeconds(seconds: Int) {
        viewModelScope.launch { prefs.setClipboardClearSeconds(seconds) }
    }

    fun clearHistory() {
        viewModelScope.launch {
            prefs.clearHistory()
            _ui.update { it.copy(infoMessage = "History cleared.") }
        }
    }

    fun removeHistoryEntry(site: String) {
        viewModelScope.launch {
            prefs.removeHistory(site)
            _ui.update { it.copy(infoMessage = "Removed \"$site\" from history.") }
        }
    }

    suspend fun exportHistory(): String = prefs.exportHistoryJson()

    fun importHistory(json: String) {
        viewModelScope.launch {
            try {
                val count = prefs.importHistoryJson(json)
                _ui.update { it.copy(infoMessage = "Imported $count site(s).") }
            } catch (e: Exception) {
                _ui.update {
                    it.copy(errorMessage = "Import failed: ${e.message}")
                }
            }
        }
    }

    // endregion

    fun biometricVault() = biometricVault

    override fun onCleared() {
        performLock()
        clipboard.cancelPendingClear()
        super.onCleared()
    }
}
