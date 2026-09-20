package com.example.ui.player

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.view.WindowManager
import kotlin.math.roundToInt

/**
 * Controller for managing Volume and Screen Brightness during video playback gestures.
 *
 * Guarantees:
 * - Does NOT access ExoPlayer directly.
 * - Manages system volume using standard Android [AudioManager.STREAM_MUSIC].
 * - Manages brightness locally on the Activity Window via [WindowManager.LayoutParams.screenBrightness]
 *   without requiring WRITE_SETTINGS system permission.
 */
class PlayerGestureController(
    private val context: Context,
    private val activity: Activity?
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    val maxVolume: Int
        get() = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 100

    val currentVolume: Int
        get() = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0

    val currentVolumePercent: Int
        get() {
            val max = maxVolume
            if (max <= 0) return 0
            return ((currentVolume.toFloat() / max.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
        }

    /**
     * Adjusts stream volume by a delta step and returns the updated percentage (0..100).
     */
    fun adjustVolume(deltaFraction: Float): Int {
        val am = audioManager ?: return 0
        val max = maxVolume
        if (max <= 0) return 0

        val current = currentVolume
        val change = (deltaFraction * max)
        val target = (current + change).roundToInt().coerceIn(0, max)

        am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        return ((target.toFloat() / max.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
    }

    /**
     * Retrieves the current window brightness percent (0..100).
     * If window brightness is not set (-1), falls back to system screen brightness.
     */
    val currentBrightnessPercent: Int
        get() {
            val window = activity?.window ?: return 50
            val lp = window.attributes
            if (lp.screenBrightness >= 0f) {
                return (lp.screenBrightness * 100f).roundToInt().coerceIn(0, 100)
            }
            // Fallback to system brightness
            return try {
                val sysBright = Settings.System.getInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    128
                )
                ((sysBright / 255f) * 100f).roundToInt().coerceIn(0, 100)
            } catch (_: Throwable) {
                50
            }
        }

    /**
     * Adjusts window brightness by a delta fraction (-1.0f .. 1.0f) and returns updated percent (0..100).
     */
    fun adjustBrightness(deltaFraction: Float): Int {
        val window = activity?.window ?: return 50
        val currentPercent = currentBrightnessPercent
        val targetPercent = (currentPercent + (deltaFraction * 100f)).roundToInt().coerceIn(0, 100)
        val targetFloat = (targetPercent / 100f).coerceIn(0.01f, 1.0f)

        val lp = window.attributes
        lp.screenBrightness = targetFloat
        window.attributes = lp
        return targetPercent
    }
}
