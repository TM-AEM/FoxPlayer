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
class SettingsViewModel @JvmOverloads constructor(
    application: Application,
    private val settingsRepository: AppSettingsRepository = AppSettingsRepositoryImpl.getInstance(application)
) : AndroidViewModel(application) {

    private val coroutineScope = viewModelScope

    constructor(
        application: Application,
        settingsRepository: AppSettingsRepository,
        scope: kotlinx.coroutines.CoroutineScope
    ) : this(application, settingsRepository) {
        // Custom scope constructor
    }

    val uiState: StateFlow<AppSettings> = settingsRepository.appSettingsFlow
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppSettings()
        )

    fun onLanguageSelected(language: AppLanguage) {
        viewModelScope.launch {
            settingsRepository.updateLanguage(language)
        }
    }

    fun onThemeSelected(theme: AppTheme) {
        viewModelScope.launch {
            settingsRepository.updateTheme(theme)
        }
    }

    fun onResumePlaybackToggled(resume: Boolean) {
        viewModelScope.launch {
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
