package com.example.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "node_studio_prefs")

data class StudioSettings(
    val isRuntimeInstalled: Boolean = false,
    val installedNodeVersion: String = "v22.14.0",
    val installedNpmVersion: String = "10.9.2",
    val themeMode: String = "dark",
    val editorFontSizeSp: Int = 14,
    val tabSize: Int = 2,
    val wordWrap: Boolean = false,
    val localHostingEnabled: Boolean = true,
    val wakeLockEnabled: Boolean = true,
    val runningNotificationEnabled: Boolean = true
)

class StudioPreferences(private val context: Context) {
    private object Keys {
        val RUNTIME_INSTALLED = booleanPreferencesKey("runtime_installed")
        val NODE_VERSION = stringPreferencesKey("node_version")
        val NPM_VERSION = stringPreferencesKey("npm_version")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val EDITOR_FONT_SIZE = intPreferencesKey("editor_font_size")
        val TAB_SIZE = intPreferencesKey("tab_size")
        val WORD_WRAP = booleanPreferencesKey("word_wrap")
        val LOCAL_HOSTING = booleanPreferencesKey("local_hosting_enabled")
        val WAKE_LOCK = booleanPreferencesKey("wake_lock_enabled")
        val RUNNING_NOTIFICATION = booleanPreferencesKey("running_notification_enabled")
    }

    val settingsFlow: Flow<StudioSettings> = context.dataStore.data.map { prefs ->
        StudioSettings(
            isRuntimeInstalled = prefs[Keys.RUNTIME_INSTALLED] ?: false,
            installedNodeVersion = prefs[Keys.NODE_VERSION] ?: "v22.14.0",
            installedNpmVersion = prefs[Keys.NPM_VERSION] ?: "10.9.2",
            themeMode = prefs[Keys.THEME_MODE] ?: "dark",
            editorFontSizeSp = prefs[Keys.EDITOR_FONT_SIZE] ?: 14,
            tabSize = prefs[Keys.TAB_SIZE] ?: 2,
            wordWrap = prefs[Keys.WORD_WRAP] ?: false,
            localHostingEnabled = prefs[Keys.LOCAL_HOSTING] ?: true,
            wakeLockEnabled = prefs[Keys.WAKE_LOCK] ?: true,
            runningNotificationEnabled = prefs[Keys.RUNNING_NOTIFICATION] ?: true
        )
    }

    suspend fun setRuntimeInstalled(installed: Boolean, nodeVer: String, npmVer: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.RUNTIME_INSTALLED] = installed
            prefs[Keys.NODE_VERSION] = nodeVer
            prefs[Keys.NPM_VERSION] = npmVer
        }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode }
    }

    suspend fun setEditorFontSize(sizeSp: Int) {
        context.dataStore.edit { it[Keys.EDITOR_FONT_SIZE] = sizeSp.coerceIn(11, 22) }
    }

    suspend fun setWordWrap(enabled: Boolean) {
        context.dataStore.edit { it[Keys.WORD_WRAP] = enabled }
    }

    suspend fun setLocalHostingEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.LOCAL_HOSTING] = enabled }
    }

    suspend fun setWakeLockEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.WAKE_LOCK] = enabled }
    }

    suspend fun setRunningNotificationEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.RUNNING_NOTIFICATION] = enabled }
    }
}
