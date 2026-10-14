package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.player.DoubleTapSeekDuration
import com.example.ui.player.GestureSensitivity
import com.example.ui.player.GestureSettingsState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.gestureSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "foxplayer_gesture_settings")

/**
 * Repository interface for reading and writing persisted gesture sensitivity preferences.
 */
interface GestureSettingsRepository {
    val gestureSettingsFlow: Flow<GestureSettingsState>
    suspend fun updateVolumeSensitivity(sensitivity: GestureSensitivity)
    suspend fun updateBrightnessSensitivity(sensitivity: GestureSensitivity)
    suspend fun updateDoubleTapSeekDuration(duration: DoubleTapSeekDuration)
    suspend fun updateDoubleTapSeekDuration(seconds: Int) = updateDoubleTapSeekDuration(DoubleTapSeekDuration.fromSeconds(seconds))
    suspend fun updateDoubleTapSeekSeconds(seconds: Int) = updateDoubleTapSeekDuration(seconds)
    suspend fun updateSettings(settings: GestureSettingsState)
}

/**
 * Implementation of [GestureSettingsRepository] powered by Preferences DataStore.
 *
 * Guarantees:
 * - Thread-safe, non-blocking asynchronous persistence using Kotlin Coroutines & Flow.
 * - Robust error handling: catches [IOException] and safely falls back to [emptyPreferences].
 * - Safe fallback parsing: unknown, missing, or invalid values always fall back to [GestureSensitivity.DEFAULT] / [DoubleTapSeekDuration.DEFAULT].
 * - Prevents redundant disk writes when the incoming value is identical to the currently stored value.
 */
class GestureSettingsRepositoryImpl(
    private val dataStore: DataStore<Preferences>
) : GestureSettingsRepository {

    constructor(context: Context) : this(context.applicationContext.gestureSettingsDataStore)

    companion object {
        val KEY_VOLUME_SENSITIVITY = stringPreferencesKey("gesture_volume_sensitivity")
        val KEY_BRIGHTNESS_SENSITIVITY = stringPreferencesKey("gesture_brightness_sensitivity")
        val KEY_DOUBLE_TAP_SEEK_DURATION = stringPreferencesKey("gesture_double_tap_seek_duration")
        val KEY_DOUBLE_TAP_SEEK_SECONDS = intPreferencesKey("gesture_double_tap_seek_seconds")

        @Volatile
        private var INSTANCE: GestureSettingsRepository? = null

        fun getInstance(context: Context): GestureSettingsRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GestureSettingsRepositoryImpl(context).also { INSTANCE = it }
            }
        }

        fun parseSensitivity(name: String?): GestureSensitivity {
            if (name.isNullOrBlank()) return GestureSensitivity.DEFAULT
            return try {
                GestureSensitivity.valueOf(name.trim().uppercase())
            } catch (_: IllegalArgumentException) {
                GestureSensitivity.DEFAULT
            }
        }

        fun parseSeekDuration(raw: String?): DoubleTapSeekDuration {
            if (raw.isNullOrBlank()) return DoubleTapSeekDuration.DEFAULT
            val trimmed = raw.trim()
            try {
                return DoubleTapSeekDuration.valueOf(trimmed.uppercase())
            } catch (_: IllegalArgumentException) {}
            val numeric = trimmed.removeSuffix("s").removeSuffix("S").trim().toIntOrNull()
            if (numeric != null) {
                return DoubleTapSeekDuration.fromSeconds(numeric)
            }
            return DoubleTapSeekDuration.DEFAULT
        }

        fun parseSeekDuration(seconds: Int?): DoubleTapSeekDuration {
            if (seconds == null) return DoubleTapSeekDuration.DEFAULT
            return DoubleTapSeekDuration.fromSeconds(seconds)
        }
    }

    override val gestureSettingsFlow: Flow<GestureSettingsState> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val volumeRaw = preferences[KEY_VOLUME_SENSITIVITY]
            val brightnessRaw = preferences[KEY_BRIGHTNESS_SENSITIVITY]
            val seekDurationString = preferences[KEY_DOUBLE_TAP_SEEK_DURATION]
            val seekDurationInt = preferences[KEY_DOUBLE_TAP_SEEK_SECONDS]

            val seekDuration = when {
                seekDurationString != null -> parseSeekDuration(seekDurationString)
                seekDurationInt != null -> parseSeekDuration(seekDurationInt)
                else -> DoubleTapSeekDuration.DEFAULT
            }

            GestureSettingsState(
                volumeSensitivity = parseSensitivity(volumeRaw),
                brightnessSensitivity = parseSensitivity(brightnessRaw),
                doubleTapSeekDuration = seekDuration
            )
        }
        .distinctUntilChanged()

    override suspend fun updateVolumeSensitivity(sensitivity: GestureSensitivity) {
        dataStore.edit { preferences ->
            if (preferences[KEY_VOLUME_SENSITIVITY] != sensitivity.name) {
                preferences[KEY_VOLUME_SENSITIVITY] = sensitivity.name
            }
        }
    }

    override suspend fun updateBrightnessSensitivity(sensitivity: GestureSensitivity) {
        dataStore.edit { preferences ->
            if (preferences[KEY_BRIGHTNESS_SENSITIVITY] != sensitivity.name) {
                preferences[KEY_BRIGHTNESS_SENSITIVITY] = sensitivity.name
            }
        }
    }

    override suspend fun updateDoubleTapSeekDuration(duration: DoubleTapSeekDuration) {
        dataStore.edit { preferences ->
            if (preferences[KEY_DOUBLE_TAP_SEEK_DURATION] != duration.name) {
                preferences[KEY_DOUBLE_TAP_SEEK_DURATION] = duration.name
            }
            if (preferences[KEY_DOUBLE_TAP_SEEK_SECONDS] != duration.seconds) {
                preferences[KEY_DOUBLE_TAP_SEEK_SECONDS] = duration.seconds
            }
        }
    }

    override suspend fun updateSettings(settings: GestureSettingsState) {
        dataStore.edit { preferences ->
            if (preferences[KEY_VOLUME_SENSITIVITY] != settings.volumeSensitivity.name) {
                preferences[KEY_VOLUME_SENSITIVITY] = settings.volumeSensitivity.name
            }
            if (preferences[KEY_BRIGHTNESS_SENSITIVITY] != settings.brightnessSensitivity.name) {
                preferences[KEY_BRIGHTNESS_SENSITIVITY] = settings.brightnessSensitivity.name
            }
            if (preferences[KEY_DOUBLE_TAP_SEEK_DURATION] != settings.doubleTapSeekDuration.name) {
                preferences[KEY_DOUBLE_TAP_SEEK_DURATION] = settings.doubleTapSeekDuration.name
            }
            if (preferences[KEY_DOUBLE_TAP_SEEK_SECONDS] != settings.doubleTapSeekDuration.seconds) {
                preferences[KEY_DOUBLE_TAP_SEEK_SECONDS] = settings.doubleTapSeekDuration.seconds
            }
        }
    }
}
