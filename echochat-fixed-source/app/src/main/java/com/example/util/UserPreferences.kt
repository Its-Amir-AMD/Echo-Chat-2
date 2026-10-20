package com.example.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.echoChatDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "echochat_preferences"
)

class UserPreferences(context: Context) {
    private val store = context.applicationContext.echoChatDataStore

    val language: Flow<AppLanguage> = store.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences ->
            if (preferences[LANGUAGE_KEY] == AppLanguage.ENGLISH.code) AppLanguage.ENGLISH
            else AppLanguage.PERSIAN
        }

    val darkMode: Flow<Boolean?> = store.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences ->
            when (preferences[THEME_KEY]) {
                "dark" -> true
                "light" -> false
                else -> null
            }
        }

    suspend fun setLanguage(language: AppLanguage) {
        store.edit { it[LANGUAGE_KEY] = language.code }
    }

    suspend fun setDarkMode(isDarkMode: Boolean?) {
        store.edit {
            it[THEME_KEY] = when (isDarkMode) {
                true -> "dark"
                false -> "light"
                null -> "system"
            }
        }
    }

    private companion object {
        val LANGUAGE_KEY = stringPreferencesKey("language")
        val THEME_KEY = stringPreferencesKey("theme")
    }
}
