package com.example.core.model

import androidx.annotation.StringRes
import com.example.R

/**
 * Supported application languages.
 */
enum class AppLanguage(val code: String, @get:StringRes val displayNameResId: Int) {
    SYSTEM("", R.string.settings_language_system),
    ARABIC("ar", R.string.settings_language_arabic),
    ENGLISH("en", R.string.settings_language_english);

    companion object {
        fun fromCode(code: String?): AppLanguage {
            return when (code?.lowercase()?.trim()) {
                "ar" -> ARABIC
                "en" -> ENGLISH
                else -> SYSTEM
            }
        }
    }
}

/**
 * Supported application themes.
 */
enum class AppTheme(@get:StringRes val displayNameResId: Int) {
    SYSTEM(R.string.settings_theme_system),
    LIGHT(R.string.settings_theme_light),
    DARK(R.string.settings_theme_dark);

    companion object {
        fun fromName(name: String?): AppTheme {
            return try {
                if (name != null) valueOf(name.uppercase().trim()) else SYSTEM
            } catch (_: IllegalArgumentException) {
                SYSTEM
            }
        }
    }
}

/**
 * Immutable state model holding global application preferences.
 */
data class AppSettings(
    val language: AppLanguage = AppLanguage.SYSTEM,
    val theme: AppTheme = AppTheme.SYSTEM,
    val resumePlayback: Boolean = true
)
