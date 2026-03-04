package com.ergou.app.util

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** 主题模式：跟随系统 / 亮色 / 暗色 */
enum class ThemeMode(val value: Int) {
    SYSTEM(0), LIGHT(1), DARK(2);
    companion object {
        fun fromValue(v: Int) = entries.firstOrNull { it.value == v } ?: SYSTEM
    }
}

class ApiKeyProvider(private val context: Context) {

    companion object {
        private val API_KEY = stringPreferencesKey("deepseek_api_key")
        private val THEME_MODE = intPreferencesKey("theme_mode")
    }

    val apiKey: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[API_KEY] ?: ""
    }

    val themeMode: Flow<ThemeMode> = context.settingsDataStore.data.map { prefs ->
        ThemeMode.fromValue(prefs[THEME_MODE] ?: 0)
    }

    suspend fun saveApiKey(key: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[API_KEY] = key
        }
    }

    suspend fun saveThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { prefs ->
            prefs[THEME_MODE] = mode.value
        }
    }
}
