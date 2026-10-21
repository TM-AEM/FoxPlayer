package com.example.ui.player

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import kotlin.math.roundToInt

/**
 * Controller for managing Volume and Screen Brightness during video playback gestures.
 *
 * Guarantees:
 * - Does NOT access ExoPlayer directly. The player gain (`Player.volume`) is never touched;
 *   the media volume gesture only drives the Android system media stream.
 * - Manages system volume using standard Android [AudioManager.STREAM_MUSIC].
 * - Manages brightness locally on the Activity Window via [WindowManager.LayoutParams.screenBrightness]
 *   without requiring WRITE_SETTINGS system permission.
 *
 * The media stream is expressed in discrete volume steps (typically 15 on most devices), so a
 * swipe that maps to less than one step cannot change the system volume on its own. To keep small
 * swipes responsive instead of silently dropping them, the gesture keeps a continuous analog
 * position in volume-step units (see [analogVolumePosition]). The analog position absorbs the
 * sub-step part of every swipe, so consecutive small swipes accumulate and eventually cross a
 * step boundary and change the system volume.
 */
class PlayerGestureController(
    private val context: Context,
    private val activity: Activity?
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    /**
     * Continuous position of the volume gesture in volume-step units. It is intentionally kept as
     * a floating point value so that the part of a swipe that does not add up to a whole step is
     * not lost. The system volume itself always stays a whole step.
     */
    private var analogVolumePosition: Float = Float.NaN

    /**
     * The system volume index this controller last applied. Used to detect volume changes that
     * were made outside of the app (hardware volume buttons, another app, an audio route change)
     * so that the analog position is re-synchronised with the real volume instead of being
     * applied on top of a value the user did not choose.
     */
    private var lastAppliedVolumeIndex: Int = -1

    /** Highest volume index supported by the media stream. */
    val maxVolume: Int
        get() = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 0

    /**
     * Lowest volume index supported by the media stream. Usually 0, but not guaranteed.
     * [AudioManager.getStreamMinVolume] was added in API 28; on older devices the media stream
     * always allows a volume of 0.
     */
    val minVolume: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            audioManager?.getStreamMinVolume(AudioManager.STREAM_MUSIC) ?: 0
        } else {
            0
        }

    /** The volume index the system currently reports for the media stream. */
    val currentVolume: Int
        get() = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0

    /** The media volume the system currently reports, as a percentage (0..100). */
    val currentVolumePercent: Int
        get() = volumePercentOf(currentVolume)

    /**
     * True when the device uses a fixed volume policy, meaning the app is not allowed to change
     * the media stream volume. On such devices [AudioManager.setStreamVolume] has no effect, so
     * the gesture cannot change the volume and only the current value is reported.
     */
    val isVolumeFixed: Boolean
        get() = audioManager?.isVolumeFixed ?: false

    /**
     * Converts a media stream volume index into the percentage shown to the user (0..100).
     */
    fun volumePercentOf(volumeIndex: Int): Int {
        val max = maxVolume
        if (max <= 0) return 0
        val min = minVolume
        // Normalize against the usable range so a non-zero minimum is not reported as a
        // misleadingly low percentage.
        val span = (max - min).toFloat()
        if (span <= 0f) return 0
        val normalized = (volumeIndex - min).coerceIn(0, max - min).toFloat() / span
        return (normalized * 100f).roundToInt().coerceIn(0, 100)
    }

    /**
     * Adjusts the system media volume via [AudioManager.STREAM_MUSIC] and returns the percentage
     * the system actually reports afterwards (0..100).
     *
     * The returned value is read back from [AudioManager] instead of being derived from the
     * requested target, because Android may refuse or adjust the request (fixed volume devices,
     * stream minimums above zero, mute handling, audio route changes).
     */
    fun adjustVolume(deltaFraction: Float): Int {
        val am = audioManager ?: return 0
        val max = maxVolume
        if (max <= 0) return 0

        // Guard against non-finite input: NaN would make every rounding below fail and
        // infinities would request an out-of-range volume.
        if (!deltaFraction.isFinite() || deltaFraction == 0f) {
            return currentVolumePercent
        }

        // On devices with a fixed volume policy the system ignores volume changes. Report the
        // real volume instead of pretending the gesture had an effect.
        if (am.isVolumeFixed) {
            return currentVolumePercent
        }

        val min = minVolume
        val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)

        // Re-synchronise the analog position with the real system volume when it was changed
        // outside of the app (hardware buttons, another app, audio route change). Otherwise the
        // gesture would keep applying a position the user did not choose.
        if (analogVolumePosition.isNaN() || current != lastAppliedVolumeIndex) {
            analogVolumePosition = current.toFloat()
        }

        // Move the continuous position by the swipe, then apply the nearest whole step.
        // Rounding (instead of truncating) keeps upward and downward swipes symmetric.
        val position = (analogVolumePosition + deltaFraction * max)
            .coerceIn(min.toFloat(), max.toFloat())
        analogVolumePosition = position

        val target = position.roundToInt().coerceIn(min, max)
        if (target != current) {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        }
        // Read back what the system actually applied: it may clamp or refuse the request.
        lastAppliedVolumeIndex = am.getStreamVolume(AudioManager.STREAM_MUSIC)

        // Report what the system actually applied, not what was requested.
        return currentVolumePercent
    }

    /**
     * Discards any volume adjustment that has not been applied to the system yet.
     * Called when a volume gesture ends so that a swipe that never reached a full step does not
     * leak into a later, unrelated gesture.
     */
    fun resetPendingVolumeAdjustment() {
        analogVolumePosition = Float.NaN
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
        if (!deltaFraction.isFinite() || deltaFraction == 0f) {
            return currentBrightnessPercent
        }
        val currentPercent = currentBrightnessPercent
        val targetPercent = (currentPercent + (deltaFraction * 100f)).roundToInt().coerceIn(0, 100)
        val targetFloat = (targetPercent / 100f).coerceIn(0.01f, 1.0f)

        val lp = window.attributes
        lp.screenBrightness = targetFloat
        window.attributes = lp
        return targetPercent
    }
}
