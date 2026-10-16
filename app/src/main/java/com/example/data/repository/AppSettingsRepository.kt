package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.model.AppLanguage
import com.example.core.model.AppSettings
import com.example.core.model.AppTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.appSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "foxplayer_app_settings")

/**
 * Repository interface for reading and writing persisted application preferences (Language, Theme, Resume).
 */
interface AppSettingsRepository {
    val appSettingsFlow: Flow<AppSettings>
    suspend fun updateLanguage(language: AppLanguage)
    suspend fun updateTheme(theme: AppTheme)
    suspend fun updateResumePlayback(resume: Boolean)
}

/**
 * Implementation of [AppSettingsRepository] powered by Preferences DataStore.
 */
class AppSettingsRepositoryImpl(
    private val dataStore: DataStore<Preferences>
) : AppSettingsRepository {

    constructor(context: Context) : this(context.applicationContext.appSettingsDataStore)

    companion object {
        val KEY_LANGUAGE = stringPreferencesKey("app_language")
        val KEY_THEME = stringPreferencesKey("app_theme")
        val KEY_RESUME_PLAYBACK = booleanPreferencesKey("app_resume_playback")

        @Volatile
        private var INSTANCE: AppSettingsRepository? = null

        fun getInstance(context: Context): AppSettingsRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppSettingsRepositoryImpl(context).also { INSTANCE = it }
            }
        }
    }

    override val appSettingsFlow: Flow<AppSettings> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val langCode = preferences[KEY_LANGUAGE]
            val themeName = preferences[KEY_THEME]
            val resume = preferences[KEY_RESUME_PLAYBACK] ?: true

            AppSettings(
                language = AppLanguage.fromCode(langCode),
                theme = AppTheme.fromName(themeName),
                resumePlayback = resume
            )
        }
        .distinctUntilChanged()

    override suspend fun updateLanguage(language: AppLanguage) {
        dataStore.edit { preferences ->
            if (preferences[KEY_LANGUAGE] != language.code) {
                preferences[KEY_LANGUAGE] = language.code
            }
        }
    }

    override suspend fun updateTheme(theme: AppTheme) {
        dataStore.edit { preferences ->
            if (preferences[KEY_THEME] != theme.name) {
                preferences[KEY_THEME] = theme.name
            }
        }
    }

    override suspend fun updateResumePlayback(resume: Boolean) {
        dataStore.edit { preferences ->
            if (preferences[KEY_RESUME_PLAYBACK] != resume) {
                preferences[KEY_RESUME_PLAYBACK] = resume
            }
        }
    }
}
