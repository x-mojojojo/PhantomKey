package com.phantomkey.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "phantomkey_prefs")

/**
 * Persists non-secret settings and site history metadata only.
 * The master password and derived keys are NEVER written here.
 */
class PreferencesRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private object Keys {
        val FULL_NAME = stringPreferencesKey("full_name")
        val SETUP_COMPLETE = booleanPreferencesKey("setup_complete")
        val BIOMETRICS_ENABLED = booleanPreferencesKey("biometrics_enabled")
        val SESSION_TIMEOUT_MINUTES = intPreferencesKey("session_timeout_minutes")
        val CLIPBOARD_CLEAR_SECONDS = intPreferencesKey("clipboard_clear_seconds")
        val DEFAULT_PASSWORD_TYPE = stringPreferencesKey("default_password_type")
        val SITE_HISTORY_JSON = stringPreferencesKey("site_history_json")
        // Salted Argon2id hash used only to verify unlock — not reversible to the password.
        val MASTER_PASSWORD_HASH = stringPreferencesKey("master_password_hash")
        val MASTER_PASSWORD_SALT = stringPreferencesKey("master_password_salt")
    }

    val fullName: Flow<String> = context.dataStore.data.map { it[Keys.FULL_NAME].orEmpty() }

    val setupComplete: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.SETUP_COMPLETE] == true }

    val biometricsEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.BIOMETRICS_ENABLED] == true }

    val sessionTimeoutMinutes: Flow<Int> =
        context.dataStore.data.map { it[Keys.SESSION_TIMEOUT_MINUTES] ?: 5 }

    val clipboardClearSeconds: Flow<Int> =
        context.dataStore.data.map { it[Keys.CLIPBOARD_CLEAR_SECONDS] ?: 30 }

    val defaultPasswordType: Flow<String> =
        context.dataStore.data.map { it[Keys.DEFAULT_PASSWORD_TYPE] ?: "MAXIMUM" }

    val siteHistory: Flow<List<SiteHistoryEntry>> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.SITE_HISTORY_JSON].orEmpty()
        if (raw.isBlank()) emptyList()
        else runCatching { json.decodeFromString<List<SiteHistoryEntry>>(raw) }.getOrDefault(emptyList())
    }

    suspend fun isSetupComplete(): Boolean = setupComplete.first()

    suspend fun getFullName(): String = fullName.first()

    suspend fun getMasterPasswordHash(): String? =
        context.dataStore.data.first()[Keys.MASTER_PASSWORD_HASH]

    suspend fun getMasterPasswordSalt(): String? =
        context.dataStore.data.first()[Keys.MASTER_PASSWORD_SALT]

    suspend fun completeSetup(
        fullName: String,
        passwordHash: String,
        passwordSalt: String,
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.FULL_NAME] = fullName
            prefs[Keys.MASTER_PASSWORD_HASH] = passwordHash
            prefs[Keys.MASTER_PASSWORD_SALT] = passwordSalt
            prefs[Keys.SETUP_COMPLETE] = true
        }
    }

    suspend fun setBiometricsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BIOMETRICS_ENABLED] = enabled }
    }

    suspend fun setSessionTimeoutMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.SESSION_TIMEOUT_MINUTES] = minutes.coerceIn(1, 60) }
    }

    suspend fun setClipboardClearSeconds(seconds: Int) {
        context.dataStore.edit { it[Keys.CLIPBOARD_CLEAR_SECONDS] = seconds.coerceIn(5, 300) }
    }

    suspend fun setDefaultPasswordType(type: String) {
        context.dataStore.edit { it[Keys.DEFAULT_PASSWORD_TYPE] = type }
    }

    suspend fun upsertHistory(entry: SiteHistoryEntry) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.SITE_HISTORY_JSON]
                ?.let { runCatching { json.decodeFromString<List<SiteHistoryEntry>>(it) }.getOrNull() }
                .orEmpty()
                .toMutableList()

            current.removeAll { it.site.equals(entry.site, ignoreCase = true) }
            current.add(0, entry.copy(lastUsedAt = System.currentTimeMillis()))
            // Cap history size
            val trimmed = current.take(200)
            prefs[Keys.SITE_HISTORY_JSON] = json.encodeToString(trimmed)
        }
    }

    suspend fun removeHistory(site: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.SITE_HISTORY_JSON]
                ?.let { runCatching { json.decodeFromString<List<SiteHistoryEntry>>(it) }.getOrNull() }
                .orEmpty()
                .filterNot { it.site.equals(site, ignoreCase = true) }
            prefs[Keys.SITE_HISTORY_JSON] = json.encodeToString(current)
        }
    }

    suspend fun clearHistory() {
        context.dataStore.edit { it[Keys.SITE_HISTORY_JSON] = "[]" }
    }

    suspend fun exportHistoryJson(): String {
        val entries = siteHistory.first()
        return json.encodeToString(HistoryExport(entries = entries))
    }

    suspend fun importHistoryJson(raw: String): Int {
        val export = json.decodeFromString<HistoryExport>(raw)
        var imported = 0
        for (entry in export.entries) {
            if (entry.site.isNotBlank()) {
                upsertHistory(entry)
                imported++
            }
        }
        return imported
    }
}
