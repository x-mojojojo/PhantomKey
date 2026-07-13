package com.phantomkey.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.compose.runtime.CompositionLocalProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.collectAsState
import com.phantomkey.app.ui.AppViewModel
import com.phantomkey.app.ui.screens.GeneratorScreen
import com.phantomkey.app.ui.screens.SettingsScreen
import com.phantomkey.app.ui.screens.SetupScreen
import com.phantomkey.app.ui.screens.UnlockScreen
import com.phantomkey.app.ui.theme.PhantomKeyTheme
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets

class MainActivity : FragmentActivity() {

    private val viewModel: AppViewModel by viewModels()

    // Holds master password temporarily while enrolling biometrics after password unlock.
    private var pendingBiometricPassword: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides this@MainActivity) {
                PhantomKeyTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        PhantomKeyRoot(
                            viewModel = viewModel,
                            biometricsAvailable = isBiometricAvailable(),
                            onRequestBiometricUnlock = { showBiometricUnlock() },
                            onRequestBiometricEnroll = { password ->
                                pendingBiometricPassword = password
                                showBiometricEnroll()
                            },
                            onDisableBiometrics = { viewModel.disableBiometrics() },
                        )
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Touch is enough; full lock is handled by session timeout.
        // Optionally lock immediately when backgrounding for max security:
        // viewModel.lockNow()
    }

    private fun isBiometricAvailable(): Boolean {
        val manager = BiometricManager.from(this)
        return manager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG,
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun showBiometricUnlock() {
        if (!viewModel.biometricVault().isEnrolled()) {
            Toast.makeText(this, "Biometrics not enrolled", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val cipher = viewModel.biometricVault().createDecryptCipher()
            val executor = ContextCompat.getMainExecutor(this)
            val prompt = BiometricPrompt(
                this,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        val crypto = result.cryptoObject?.cipher
                        if (crypto != null) {
                            viewModel.unlockWithBiometricCipher(crypto)
                        } else {
                            Toast.makeText(
                                this@MainActivity,
                                "Biometric crypto unavailable",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                            errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                        ) {
                            Toast.makeText(this@MainActivity, errString, Toast.LENGTH_SHORT).show()
                        }
                    }
                },
            )
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock PhantomKey")
                .setSubtitle("Authenticate to decrypt your master key")
                .setNegativeButtonText("Use password")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build()
            prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Biometric unlock unavailable. Use your password.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun showBiometricEnroll() {
        val password = pendingBiometricPassword
        if (password == null) {
            Toast.makeText(
                this,
                "Unlock with password first, then enable biometrics.",
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        try {
            val cipher = viewModel.biometricVault().createEncryptCipher()
            val executor = ContextCompat.getMainExecutor(this)
            val prompt = BiometricPrompt(
                this,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        val crypto = result.cryptoObject?.cipher
                        if (crypto != null) {
                            viewModel.enrollBiometrics(crypto, password)
                            pendingBiometricPassword = null
                        }
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        pendingBiometricPassword = null
                        if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                            errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                        ) {
                            Toast.makeText(this@MainActivity, errString, Toast.LENGTH_SHORT).show()
                        }
                    }
                },
            )
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Enable biometric unlock")
                .setSubtitle("Confirm biometrics to protect your master password")
                .setNegativeButtonText("Cancel")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build()
            prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
        } catch (e: Exception) {
            pendingBiometricPassword = null
            Toast.makeText(this, "Could not enable biometrics: ${e.message}", Toast.LENGTH_LONG)
                .show()
        }
    }
}

private enum class RootScreen { Loading, Setup, Unlock, Generator, Settings }

@Composable
private fun PhantomKeyRoot(
    viewModel: AppViewModel,
    biometricsAvailable: Boolean,
    onRequestBiometricUnlock: () -> Unit,
    onRequestBiometricEnroll: (password: String) -> Unit,
    onDisableBiometrics: () -> Unit,
) {
    val state by viewModel.ui.collectAsState()
    var screen by remember { mutableStateOf(RootScreen.Loading) }
    var lastPasswordForBiometrics by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val json = viewModel.exportHistory()
                    viewModel.getApplication<PhantomKeyApp>().contentResolver
                        .openOutputStream(uri)
                        ?.use { it.write(json.toByteArray(StandardCharsets.UTF_8)) }
                    Toast.makeText(
                        viewModel.getApplication(),
                        "History exported",
                        Toast.LENGTH_SHORT,
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(
                        viewModel.getApplication(),
                        "Export failed: ${e.message}",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val text = viewModel.getApplication<PhantomKeyApp>().contentResolver
                        .openInputStream(uri)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        .orEmpty()
                    viewModel.importHistory(text)
                } catch (e: Exception) {
                    Toast.makeText(
                        viewModel.getApplication(),
                        "Import failed: ${e.message}",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    LaunchedEffect(state.ready, state.setupComplete, state.unlocked, screen) {
        if (!state.ready) {
            screen = RootScreen.Loading
            return@LaunchedEffect
        }
        when {
            !state.setupComplete -> screen = RootScreen.Setup
            !state.unlocked -> {
                if (screen != RootScreen.Settings) screen = RootScreen.Unlock
            }
            screen == RootScreen.Setup || screen == RootScreen.Unlock || screen == RootScreen.Loading -> {
                screen = RootScreen.Generator
            }
        }
    }

    when (screen) {
        RootScreen.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        RootScreen.Setup -> {
            SetupScreen(
                busy = state.busy,
                errorMessage = state.errorMessage,
                onContinue = { name, password, confirm ->
                    lastPasswordForBiometrics = password
                    viewModel.completeSetup(name, password, confirm)
                },
            )
        }

        RootScreen.Unlock -> {
            UnlockScreen(
                fullName = state.fullName,
                busy = state.busy,
                biometricsEnabled = state.biometricsEnabled && biometricsAvailable,
                errorMessage = state.errorMessage,
                onUnlock = { password ->
                    lastPasswordForBiometrics = password
                    viewModel.unlockWithPassword(password)
                },
                onBiometric = onRequestBiometricUnlock,
            )
        }

        RootScreen.Generator -> {
            GeneratorScreen(
                state = state,
                onSiteChanged = viewModel::onSiteChanged,
                onCounterChanged = viewModel::onCounterChanged,
                onIncrementCounter = viewModel::incrementCounter,
                onDecrementCounter = viewModel::decrementCounter,
                onTypeChanged = viewModel::onPasswordTypeChanged,
                onTogglePasswordVisibility = viewModel::togglePasswordVisibility,
                onCopyPassword = viewModel::copyPassword,
                onCopyLogin = viewModel::copyLogin,
                onSelectHistory = viewModel::selectHistoryEntry,
                onDeleteHistory = { entry -> viewModel.removeHistoryEntry(entry.site) },
                onOpenSettings = { screen = RootScreen.Settings },
                onLock = {
                    lastPasswordForBiometrics = null
                    viewModel.lockNow()
                },
                onTouch = viewModel::touchSession,
                onClearMessages = viewModel::clearMessages,
            )
        }

        RootScreen.Settings -> {
            SettingsScreen(
                state = state,
                biometricsHardwareAvailable = biometricsAvailable,
                onBack = {
                    screen = if (state.unlocked) RootScreen.Generator else RootScreen.Unlock
                },
                onSessionTimeoutChanged = viewModel::setSessionTimeout,
                onClipboardClearChanged = viewModel::setClipboardClearSeconds,
                onToggleBiometrics = { enable ->
                    if (enable) {
                        val pw = lastPasswordForBiometrics
                        if (pw != null) {
                            onRequestBiometricEnroll(pw)
                        } else {
                            Toast.makeText(
                                viewModel.getApplication(),
                                "Lock and unlock with your password, then enable biometrics.",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    } else {
                        onDisableBiometrics()
                    }
                },
                onExport = {
                    exportLauncher.launch("phantomkey-history.json")
                },
                onImport = {
                    importLauncher.launch(arrayOf("application/json", "text/*"))
                },
                onClearHistory = viewModel::clearHistory,
            )
        }
    }
}
