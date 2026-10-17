package com.example.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.model.AppLanguage
import com.example.core.model.AppSettings
import com.example.core.model.AppTheme
import com.example.data.repository.AppSettingsRepository
import com.example.data.repository.AppSettingsRepositoryImpl
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel managing global application preferences state and mutation.
 */
class SettingsViewModel(
    application: Application,
    private val settingsRepository: AppSettingsRepository = AppSettingsRepositoryImpl.getInstance(application),
    scope: kotlinx.coroutines.CoroutineScope? = null
) : AndroidViewModel(application) {

    private val effectiveScope: kotlinx.coroutines.CoroutineScope = scope ?: viewModelScope

    val uiState: StateFlow<AppSettings> = settingsRepository.appSettingsFlow
        .stateIn(
            scope = effectiveScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppSettings()
        )

    fun onLanguageSelected(language: AppLanguage) {
        effectiveScope.launch {
            settingsRepository.updateLanguage(language)
        }
    }

    fun onThemeSelected(theme: AppTheme) {
        effectiveScope.launch {
            settingsRepository.updateTheme(theme)
        }
    }

    fun onResumePlaybackToggled(resume: Boolean) {
        effectiveScope.launch {
            settingsRepository.updateResumePlayback(resume)
        }
    }

    class Factory(
        private val application: Application,
        private val settingsRepository: AppSettingsRepository? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return if (settingsRepository != null) {
                SettingsViewModel(application, settingsRepository) as T
            } else {
                SettingsViewModel(application) as T
            }
        }
    }
}
