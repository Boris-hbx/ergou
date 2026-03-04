package com.ergou.app.util

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class ApiKeyProvider(private val context: Context) {

    companion object {
        private val API_KEY = stringPreferencesKey("deepseek_api_key")
    }

    val apiKey: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[API_KEY] ?: ""
    }

    suspend fun saveApiKey(key: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[API_KEY] = key
        }
    }
}
